package io.jenkins.plugins.swarmcloud.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ContainerStartupDiagnostics#hintFor(String)}.
 *
 * <p>Covers the two observed shapes of OCI task-start failures we want to translate into
 * an actionable {@code HINT:} line in the build log: image without ENTRYPOINT (URL exec'd
 * as binary) and a missing custom entrypoint binary. Also covers nulls and unrecognised
 * messages to make sure we don't emit a misleading hint.</p>
 */
class ContainerStartupDiagnosticsTest {

    @Test
    void nullErrorReturnsNoHint() {
        assertNull(ContainerStartupDiagnostics.hintFor(null));
    }

    @Test
    void unrelatedErrorReturnsNoHint() {
        assertNull(ContainerStartupDiagnostics.hintFor("network: connection refused"));
        assertNull(ContainerStartupDiagnostics.hintFor(""));
        assertNull(ContainerStartupDiagnostics.hintFor("image jenkins/inbound-agent:alpine not found"));
    }

    @Test
    void execNotFoundOfHttpUrlIsRecognizedAsMissingEntrypoint() {
        // Exact shape observed in issue #10 follow-up:
        String swarmTaskErr = "starting container failed: failed to create task for container: "
                + "failed to create shim task: OCI runtime create failed: runc create failed: "
                + "unable to start container process: exec: \"https://jenkins.example.com:8080/\": "
                + "stat https://jenkins.example.com:8080/: no such file or directory: unknown";

        String hint = ContainerStartupDiagnostics.hintFor(swarmTaskErr);
        assertNotNull(hint, "URL-shaped exec failure must produce a hint");
        assertTrue(hint.contains("no ENTRYPOINT"),
                "Hint must explain the root cause: " + hint);
        assertTrue(hint.contains("disableContainerArgs"),
                "Hint must mention the disableContainerArgs workaround: " + hint);
        assertTrue(hint.contains("entrypoint:"),
                "Hint must mention the entrypoint template option: " + hint);
        assertTrue(hint.contains("jenkins/inbound-agent"),
                "Hint must point to the canonical image: " + hint);
    }

    @Test
    void execNotFoundOfHttpsUrlAlsoRecognized() {
        String err = "exec: \"http://jenkins/\": no such file or directory";
        String hint = ContainerStartupDiagnostics.hintFor(err);
        assertNotNull(hint);
        assertTrue(hint.contains("no ENTRYPOINT"));
    }

    @Test
    void execNotFoundOfBinaryPathHintsAtMissingEntrypoint() {
        String err = "OCI runtime create failed: exec: \"/usr/local/bin/my-startup.sh\": "
                + "no such file or directory: unknown";

        String hint = ContainerStartupDiagnostics.hintFor(err);
        assertNotNull(hint);
        assertTrue(hint.contains("/usr/local/bin/my-startup.sh"),
                "Hint must echo the failing binary path: " + hint);
        assertTrue(hint.contains("not found inside the image"),
                "Hint must explain the binary is missing: " + hint);
        assertTrue(hint.contains("disableContainerArgs"),
                "Hint must offer the env-vars-only fallback: " + hint);
    }

    @Test
    void hintsAreSingleLineForBuildLog() {
        // The launcher prefixes the hint with "HINT: " and prints it as a single
        // line, so the message must not contain newlines.
        String err = "exec: \"https://jenkins/\": no such file or directory";
        String hint = ContainerStartupDiagnostics.hintFor(err);
        assertNotNull(hint);
        assertEquals(-1, hint.indexOf('\n'),
                "Hint must be single-line for the build log: " + hint);
    }
}
