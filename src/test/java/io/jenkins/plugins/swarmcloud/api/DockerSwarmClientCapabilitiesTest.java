package io.jenkins.plugins.swarmcloud.api;

import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins that {@link DockerSwarmClient#applyAdvancedContainerOptions} actually
 * propagates {@code capAdd} / {@code capDrop} from the template into the spec.
 * Regression test for issue #6.
 */
class DockerSwarmClientCapabilitiesTest {

    @Test
    void appliesCapAddAndCapDropFromTemplate() throws Exception {
        try (DockerSwarmClient client = new DockerSwarmClient("tcp://127.0.0.1:1", null)) {
            ContainerSpecWithCapabilities spec = new ContainerSpecWithCapabilities();
            SwarmAgentTemplate template = new SwarmAgentTemplate("test");
            template.setCapAdd(List.of("CAP_NET_ADMIN"));
            template.setCapDrop(List.of("CAP_CHOWN"));

            client.applyAdvancedContainerOptions(spec, template);

            assertEquals(List.of("CAP_NET_ADMIN"), spec.getCapabilityAdd());
            assertEquals(List.of("CAP_CHOWN"), spec.getCapabilityDrop());
        }
    }

    @Test
    void leavesCapabilityFieldsNullWhenTemplateHasNone() throws Exception {
        try (DockerSwarmClient client = new DockerSwarmClient("tcp://127.0.0.1:1", null)) {
            ContainerSpecWithCapabilities spec = new ContainerSpecWithCapabilities();
            SwarmAgentTemplate template = new SwarmAgentTemplate("test");
            // capAdd / capDrop default to empty lists per SwarmAgentTemplate getters.

            client.applyAdvancedContainerOptions(spec, template);

            assertNull(spec.getCapabilityAdd(),
                    "no capAdd on template => capabilityAdd stays null on spec (omitted from wire JSON)");
            assertNull(spec.getCapabilityDrop(),
                    "no capDrop on template => capabilityDrop stays null on spec (omitted from wire JSON)");
        }
    }
}
