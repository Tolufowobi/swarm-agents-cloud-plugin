package io.jenkins.plugins.swarmcloud;

import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Regression tests for issue #7: per-template oneShot flag selects the
 * correct Jenkins retention strategy.
 */
@WithJenkins
class SwarmAgentOneShotTest {

    @Test
    void oneShotTemplateYieldsOnceRetentionStrategy(JenkinsRule j) throws Exception {
        SwarmAgentTemplate template = new SwarmAgentTemplate("once");
        template.setImage("jenkins/inbound-agent:latest");
        template.setOneShot(true);

        SwarmAgent agent = new SwarmAgent("once-agent", template, "cloud", "service-id");
        RetentionStrategy<?> strategy = agent.getRetentionStrategy();

        assertInstanceOf(SwarmOnceRetentionStrategy.class, strategy,
                "oneShot=true must yield SwarmOnceRetentionStrategy");
    }

    @Test
    void regularTemplateYieldsSwarmRetentionStrategy(JenkinsRule j) throws Exception {
        SwarmAgentTemplate template = new SwarmAgentTemplate("idle");
        template.setImage("jenkins/inbound-agent:latest");
        // template.setOneShot defaults to false

        SwarmAgent agent = new SwarmAgent("idle-agent", template, "cloud", "service-id");
        RetentionStrategy<?> strategy = agent.getRetentionStrategy();

        assertInstanceOf(SwarmRetentionStrategy.class, strategy,
                "default template must yield SwarmRetentionStrategy");
    }

    @Test
    void numExecutorsValidationRejectsOneShotPlusMultipleExecutors(JenkinsRule j) {
        SwarmAgentTemplate.DescriptorImpl descriptor = new SwarmAgentTemplate.DescriptorImpl();

        // The conflict: oneShot=true with numExecutors > 1.
        FormValidation conflict = descriptor.doCheckNumExecutors(2, true);
        assertEquals(FormValidation.Kind.ERROR, conflict.kind,
                "oneShot=true with numExecutors=2 must produce a validation error");

        // OK paths.
        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckNumExecutors(1, true).kind,
                "oneShot=true with numExecutors=1 must be OK");
        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckNumExecutors(2, false).kind,
                "oneShot=false with any numExecutors must be OK (no constraint)");
    }
}
