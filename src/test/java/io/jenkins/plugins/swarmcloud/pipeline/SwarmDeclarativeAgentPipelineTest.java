package io.jenkins.plugins.swarmcloud.pipeline;

import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end pipeline smoke tests for the declarative {@code agent { swarmAgent { ... } }} directive.
 *
 * <p>These tests run a real {@link WorkflowJob} through the declarative pipeline parser, model
 * interpreter, and {@link SwarmDeclarativeAgentScript}, then inspect the build log to verify
 * that the directive parses, the wiring locates the right cloud/template, and the failfast
 * error paths surface a useful message instead of cryptic {@code "Swarm cloud not found: null"}.
 * They do not exercise real Docker provisioning — the build is expected to fail when the
 * Swarm step tries to talk to a fake Docker host, which is the verification anchor that the
 * declarative directive successfully delegated to the {@code swarmAgent} step.</p>
 */
@WithJenkins
class SwarmDeclarativeAgentPipelineTest {

    @Test
    void declarativeDirectiveFailsClearlyWhenNoSwarmCloudIsConfigured(JenkinsRule j) throws Exception {
        // No SwarmCloud added to Jenkins — resolveCloudName() returns null.
        WorkflowJob job = j.createProject(WorkflowJob.class, "no-cloud");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n"
                        + "    agent { swarmAgent { label 'docker' } }\n"
                        + "    stages { stage('noop') { steps { echo 'unreached' } } }\n"
                        + "}\n",
                true));

        WorkflowRun run = j.assertBuildStatus(
                hudson.model.Result.FAILURE,
                job.scheduleBuild2(0));
        assertNotNull(run, "build must have produced a run");
        String log = j.getLog(run);
        assertTrue(log.contains("cannot resolve Docker Swarm cloud"),
                "Expected friendly failfast message in build log, got:\n" + log);
    }

    @Test
    void declarativeDirectiveFailsClearlyWhenMultipleCloudsAreAmbiguous(JenkinsRule j) throws Exception {
        // Two SwarmClouds without explicit selection — resolveCloudName() returns null.
        j.jenkins.clouds.add(new SwarmCloud("swarm-a"));
        j.jenkins.clouds.add(new SwarmCloud("swarm-b"));

        WorkflowJob job = j.createProject(WorkflowJob.class, "ambiguous-cloud");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n"
                        + "    agent { swarmAgent { label 'docker' } }\n"
                        + "    stages { stage('noop') { steps { echo 'unreached' } } }\n"
                        + "}\n",
                true));

        WorkflowRun run = j.assertBuildStatus(
                hudson.model.Result.FAILURE,
                job.scheduleBuild2(0));
        assertNotNull(run);
        String log = j.getLog(run);
        assertTrue(log.contains("cannot resolve Docker Swarm cloud"),
                "Expected friendly failfast message in build log, got:\n" + log);
    }

    @Test
    void declarativeDirectiveDelegatesToSwarmAgentStepWhenCloudResolves(JenkinsRule j) throws Exception {
        // Configure a cloud with an obviously-bad Docker host. The declarative directive should
        // parse, the script should resolve the cloud, the swarmAgent step should be invoked,
        // and provisioning should fail at the Docker layer. This anchors that the directive
        // wires through to the underlying step rather than failing earlier at parse / resolution.
        SwarmCloud cloud = new SwarmCloud("swarm");
        cloud.setDockerHost("tcp://127.0.0.1:1"); // closed port — connect attempt fails fast
        SwarmAgentTemplate tpl = new SwarmAgentTemplate("docker");
        tpl.setLabelString("docker");
        cloud.setTemplates(List.of(tpl));
        j.jenkins.clouds.add(cloud);

        WorkflowJob job = j.createProject(WorkflowJob.class, "delegates");
        job.setDefinition(new CpsFlowDefinition(
                "pipeline {\n"
                        + "    agent { swarmAgent { cloud 'swarm'; template 'docker' } }\n"
                        + "    stages { stage('noop') { steps { echo 'unreached' } } }\n"
                        + "}\n",
                true));

        WorkflowRun run = j.assertBuildStatus(
                hudson.model.Result.FAILURE,
                job.scheduleBuild2(0));
        assertNotNull(run);
        String log = j.getLog(run);
        // Anchor: provisioning was attempted. We don't pin the exact failure message because
        // Docker client errors are environment-specific, but we assert the directive at least
        // reached the swarmAgent step (it printed its banner) and did NOT die at parse time.
        assertTrue(
                log.contains("Swarm Agent Pipeline Step")
                        || log.contains("Provisioning Swarm agent")
                        || log.contains("Failed to provision"),
                "Expected swarmAgent step to have been invoked, got log:\n" + log);
    }
}
