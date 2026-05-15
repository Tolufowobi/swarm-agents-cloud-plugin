package io.jenkins.plugins.swarmcloud.diagnostics;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based diagnostics for Docker / OCI runtime errors observed when a Swarm task
 * fails to start the agent container. The goal is to convert the cryptic
 * {@code "exec: \"...\": no such file or directory"} message into a one-line actionable
 * hint that points the user at the right knob ({@code disableContainerArgs},
 * {@code entrypoint:}, or switching to {@code jenkins/inbound-agent}).
 *
 * <p>Used by {@code SwarmComputerLauncher} when polling the failing Swarm task's
 * {@code Status.Err} field.</p>
 */
public final class ContainerStartupDiagnostics {

    /**
     * Captures the first quoted token of {@code exec: "..."} in an OCI runtime error.
     * Docker/runc emit messages like:
     * <pre>OCI runtime create failed: ... exec: "https://jenkins/": stat https://jenkins/: no such file or directory</pre>
     * The capture group is the would-be executable.
     */
    private static final Pattern EXEC_NOT_FOUND = Pattern.compile(
            "exec:\\s*\"([^\"]+)\".*?no such file or directory",
            Pattern.DOTALL);

    private ContainerStartupDiagnostics() {
        // utility
    }

    /**
     * Returns a human-readable hint for a Swarm task error string, or {@code null} if no
     * known pattern matches.
     *
     * <p>Recognised patterns:</p>
     * <ul>
     *   <li><b>Args used as command (no ENTRYPOINT in image).</b> The would-be executable is
     *       an {@code http(s)://} URL — that is the Jenkins URL the plugin passes as the
     *       first positional arg. Recommend {@code disableContainerArgs: true}, setting
     *       {@code entrypoint:} on the template, or switching to {@code jenkins/inbound-agent}.</li>
     *   <li><b>Custom entrypoint binary missing.</b> The would-be executable is a path
     *       ({@code /usr/...} or {@code bin/...}) that does not exist in the image. Hint the
     *       user to check the {@code entrypoint:} value or the image's installed binaries.</li>
     * </ul>
     *
     * @param taskError the {@code Task.Status.Err} string reported by Docker Swarm; may be {@code null}
     * @return a single-line hint suitable for the build log, or {@code null} when nothing matches
     */
    @CheckForNull
    public static String hintFor(@CheckForNull String taskError) {
        if (taskError == null) {
            return null;
        }
        Matcher m = EXEC_NOT_FOUND.matcher(taskError);
        if (!m.find()) {
            return null;
        }
        String failedExec = m.group(1);
        if (looksLikeUrl(failedExec)) {
            return "The image appears to have no ENTRYPOINT, so the Jenkins URL "
                    + "(passed as the first positional argument) is being executed as a binary. "
                    + "Fix: use jenkins/inbound-agent, set 'entrypoint:' on the template, or set "
                    + "'disableContainerArgs: true' to rely on JENKINS_URL / JENKINS_SECRET / "
                    + "JENKINS_AGENT_NAME environment variables instead.";
        }
        return "The container's entrypoint binary '" + failedExec + "' was not found inside the image. "
                + "Verify the path exists in the image or remove 'entrypoint:' from the template; "
                + "for slim images, prefer 'disableContainerArgs: true' and read JENKINS_URL / "
                + "JENKINS_SECRET / JENKINS_AGENT_NAME from the environment.";
    }

    private static boolean looksLikeUrl(@NonNull String s) {
        return s.startsWith("http://") || s.startsWith("https://");
    }
}
