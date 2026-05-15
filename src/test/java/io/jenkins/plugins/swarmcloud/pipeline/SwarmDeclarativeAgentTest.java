package io.jenkins.plugins.swarmcloud.pipeline;

import hudson.ExtensionList;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SwarmDeclarativeAgent}.
 *
 * <p>Verifies the data model, cloud / label auto-resolution behaviour, and the
 * extension wiring expected by the Declarative Pipeline Directive Generator.</p>
 */
@WithJenkins
class SwarmDeclarativeAgentTest {

    private JenkinsRule jenkins;

    @BeforeEach
    void setUp(JenkinsRule jenkins) {
        this.jenkins = jenkins;
    }

    @Test
    void defaultsAreEmpty() {
        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();

        assertNull(agent.getCloud());
        assertNull(agent.getTemplate());
        assertNull(agent.getImage());
        assertNull(agent.getLabel());
        assertNull(agent.getCpuLimit());
        assertNull(agent.getMemoryLimit());
        assertNull(agent.getCustomWorkspace());
        assertEquals(0, agent.getNumExecutors());
        assertEquals(0, agent.getIdleTimeout());
        assertEquals(0, agent.getConnectionTimeout());
    }

    @Test
    void settersStoreValues() {
        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        agent.setCloud("docker-swarm");
        agent.setTemplate("maven");
        agent.setImage("jenkins/inbound-agent:alpine");
        agent.setLabel("maven java");
        agent.setNumExecutors(4);
        agent.setCpuLimit("2.0");
        agent.setMemoryLimit("4g");
        agent.setIdleTimeout(120);
        agent.setConnectionTimeout(600);
        agent.setCustomWorkspace("/ws");

        assertEquals("docker-swarm", agent.getCloud());
        assertEquals("maven", agent.getTemplate());
        assertEquals("jenkins/inbound-agent:alpine", agent.getImage());
        assertEquals("maven java", agent.getLabel());
        assertEquals(4, agent.getNumExecutors());
        assertEquals("2.0", agent.getCpuLimit());
        assertEquals("4g", agent.getMemoryLimit());
        assertEquals(120, agent.getIdleTimeout());
        assertEquals(600, agent.getConnectionTimeout());
        assertEquals("/ws", agent.getCustomWorkspace());
    }

    @Test
    void settersTrimAndNullifyEmptyStrings() {
        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        agent.setCloud("  docker-swarm  ");
        agent.setLabel("");
        agent.setImage("   ");

        assertEquals("docker-swarm", agent.getCloud());
        assertNull(agent.getLabel());
        assertNull(agent.getImage());
    }

    @Test
    void resolveCloudNameReturnsExplicitValue() {
        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        agent.setCloud("explicit-cloud");

        assertEquals("explicit-cloud", agent.resolveCloudName());
    }

    @Test
    void resolveCloudNameReturnsNullWhenNoCloudsConfigured() {
        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        assertNull(agent.resolveCloudName());
    }

    @Test
    void resolveCloudNameAutoDetectsSingleSwarmCloud() {
        SwarmCloud cloud = new SwarmCloud("only-swarm");
        jenkins.jenkins.clouds.add(cloud);

        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        assertEquals("only-swarm", agent.resolveCloudName());
    }

    @Test
    void resolveCloudNameIsAmbiguousWithMultipleSwarmClouds() {
        jenkins.jenkins.clouds.add(new SwarmCloud("swarm-a"));
        jenkins.jenkins.clouds.add(new SwarmCloud("swarm-b"));

        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        assertNull(agent.resolveCloudName(),
                "Multiple swarm clouds without explicit selection must not be auto-resolved");
    }

    @Test
    void resolveLabelReturnsExplicitValue() {
        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        agent.setLabel("explicit-label");

        assertEquals("explicit-label", agent.resolveLabel());
    }

    @Test
    void resolveLabelFallsBackToTemplateLabel() {
        SwarmCloud cloud = new SwarmCloud("swarm");
        SwarmAgentTemplate tpl = new SwarmAgentTemplate("maven");
        tpl.setLabelString("maven java");
        cloud.setTemplates(List.of(tpl));
        jenkins.jenkins.clouds.add(cloud);

        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        agent.setCloud("swarm");
        agent.setTemplate("maven");

        assertEquals("maven java", agent.resolveLabel());
    }

    @Test
    void resolveLabelReturnsNullWhenNeitherSourceAvailable() {
        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        assertNull(agent.resolveLabel());
    }

    @Test
    void resolveLabelReturnsNullWhenTemplateMissing() {
        SwarmCloud cloud = new SwarmCloud("swarm");
        jenkins.jenkins.clouds.add(cloud);

        SwarmDeclarativeAgent agent = new SwarmDeclarativeAgent();
        agent.setCloud("swarm");
        agent.setTemplate("nonexistent");

        assertNull(agent.resolveLabel());
    }

    @Test
    void descriptorRegisteredWithSwarmAgentSymbol() {
        ExtensionList<DeclarativeAgentDescriptor> all =
                jenkins.jenkins.getExtensionList(DeclarativeAgentDescriptor.class);

        boolean found = all.stream()
                .anyMatch(d -> d instanceof SwarmDeclarativeAgent.DescriptorImpl);

        assertTrue(found, "SwarmDeclarativeAgent.DescriptorImpl must be registered as a DeclarativeAgentDescriptor");
    }

    @Test
    void descriptorDisplayName() {
        SwarmDeclarativeAgent.DescriptorImpl descriptor = new SwarmDeclarativeAgent.DescriptorImpl();
        assertEquals("Docker Swarm Agent", descriptor.getDisplayName());
    }

    @Test
    void doFillCloudItemsIncludesAutoOptionAndConfiguredClouds() {
        jenkins.jenkins.clouds.add(new SwarmCloud("first"));
        jenkins.jenkins.clouds.add(new SwarmCloud("second"));

        SwarmDeclarativeAgent.DescriptorImpl descriptor = new SwarmDeclarativeAgent.DescriptorImpl();
        var items = descriptor.doFillCloudItems();

        assertNotNull(items);
        // First entry is the "(auto)" sentinel mapping to "".
        assertEquals("", items.get(0).value);
        assertTrue(items.stream().anyMatch(o -> "first".equals(o.value)));
        assertTrue(items.stream().anyMatch(o -> "second".equals(o.value)));
    }
}
