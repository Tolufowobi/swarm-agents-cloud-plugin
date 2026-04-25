package io.jenkins.plugins.swarmcloud.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the JSON wire format produced by {@link ContainerSpecWithCapabilities}.
 * Regression test for issue #6: capability fields must reach the Docker Swarm API.
 */
class ContainerSpecWithCapabilitiesTest {

    @Test
    void serializesCapabilityFieldsWhenSet() throws Exception {
        ContainerSpecWithCapabilities spec = new ContainerSpecWithCapabilities();
        spec.withImage("jenkins/inbound-agent:latest");
        spec.withCapabilityAdd(List.of("CAP_NET_ADMIN", "CAP_SYS_ADMIN"));
        spec.withCapabilityDrop(List.of("CAP_AUDIT_WRITE"));

        String json = new ObjectMapper().writeValueAsString(spec);

        assertTrue(json.contains("\"CapabilityAdd\":[\"CAP_NET_ADMIN\",\"CAP_SYS_ADMIN\"]"),
                "JSON must include CapabilityAdd array; got: " + json);
        assertTrue(json.contains("\"CapabilityDrop\":[\"CAP_AUDIT_WRITE\"]"),
                "JSON must include CapabilityDrop array; got: " + json);
        assertTrue(json.contains("\"Image\":\"jenkins/inbound-agent:latest\""),
                "JSON must include inherited Image field; got: " + json);
    }

    @Test
    void omitsCapabilityFieldsWhenNull() throws Exception {
        ContainerSpecWithCapabilities spec = new ContainerSpecWithCapabilities();
        spec.withImage("jenkins/inbound-agent:latest");

        String json = new ObjectMapper().writeValueAsString(spec);

        assertTrue(json.contains("\"Image\":\"jenkins/inbound-agent:latest\""),
                "JSON must include inherited Image field; got: " + json);
        assertFalse(json.contains("CapabilityAdd"),
                "JSON must not include CapabilityAdd when not set; got: " + json);
        assertFalse(json.contains("CapabilityDrop"),
                "JSON must not include CapabilityDrop when not set; got: " + json);
    }
}
