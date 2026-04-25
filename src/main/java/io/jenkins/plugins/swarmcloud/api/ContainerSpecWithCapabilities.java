package io.jenkins.plugins.swarmcloud.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.dockerjava.api.model.ContainerSpec;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;

/**
 * Extension of {@link ContainerSpec} that publishes the {@code CapabilityAdd}
 * and {@code CapabilityDrop} fields of Docker Swarm's {@code TaskSpec.ContainerSpec}.
 * <p>
 * Required because {@code docker-java-api 3.7.0} (the version pulled in
 * via the Jenkins BOM) does not expose those fields on the typed model,
 * even though Docker API 1.41+ (Docker 20.10+) supports them on the wire.
 * <p>
 * Field-level {@code @JsonInclude(NON_NULL)} keeps the new fields out of
 * the JSON when not set; inherited fields are unaffected and serialize
 * exactly as the parent does.
 */
@SuppressFBWarnings(value = "EQ_DOESNT_OVERRIDE_EQUALS",
        justification = "Thin Jackson DTO; instances are constructed, "
                + "serialized once, and discarded — never used as map keys or "
                + "stored in sets, so inherited equals/hashCode (which ignores "
                + "the two added fields) is acceptable.")
public class ContainerSpecWithCapabilities extends ContainerSpec {

    private static final long serialVersionUID = 1L;

    @JsonProperty("CapabilityAdd")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    private List<String> capabilityAdd;

    @JsonProperty("CapabilityDrop")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    private List<String> capabilityDrop;

    public ContainerSpecWithCapabilities withCapabilityAdd(List<String> capabilityAdd) {
        this.capabilityAdd = capabilityAdd;
        return this;
    }

    public ContainerSpecWithCapabilities withCapabilityDrop(List<String> capabilityDrop) {
        this.capabilityDrop = capabilityDrop;
        return this;
    }

    @Nullable
    public List<String> getCapabilityAdd() {
        return capabilityAdd;
    }

    @Nullable
    public List<String> getCapabilityDrop() {
        return capabilityDrop;
    }
}
