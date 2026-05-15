package io.jenkins.plugins.swarmcloud;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;

import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for CasC <strong>export</strong> of {@link SwarmCloud}.
 *
 * <p>{@link SwarmAgentTemplate} historically exposed many @DataBoundSetter aliases
 * (label/labelString, workingDir/remoteFs, cpuLimit/limitsNanoCPUs, memoryLimit/limitsMemoryBytes,
 * mounts/hostBinds, environmentVariables/envVars, several *String list variants, etc.) for
 * docker-swarm-plugin import compatibility. The CasC {@code DataBoundConfigurator} treats every
 * setter as a separate attribute, so the exported YAML used to contain a duplicate sibling key
 * for every alias backing the same field.</p>
 *
 * <p>These tests pin down the cleaned-up export — single canonical key per field, no duplicates.</p>
 */
@WithJenkinsConfiguredWithCode
class CascExportTest {

    /** Pairs of (alias, canonical) keys that must NOT both appear in the same template mapping. */
    private static final List<String[]> ALIAS_PAIRS = Arrays.asList(
            new String[] {"label", "labelString"},
            new String[] {"workingDir", "remoteFs"},
            new String[] {"limitsNanoCPUs", "cpuLimit"},
            new String[] {"limitsMemoryBytes", "memoryLimit"},
            new String[] {"reservationsNanoCPUs", "cpuReservation"},
            new String[] {"reservationsMemoryBytes", "memoryReservation"},
            new String[] {"hostBinds", "mounts"},
            new String[] {"envVars", "environmentVariables"},
            new String[] {"placementConstraintsString", "placementConstraints"},
            new String[] {"networkAliasesString", "networkAliases"},
            new String[] {"configsString", "configs"},
            new String[] {"cacheDirsString", "cacheDirs"},
            new String[] {"capAddString", "capAdd"},
            new String[] {"capDropString", "capDrop"},
            new String[] {"sysctlsString", "sysctls"},
            new String[] {"dnsServersString", "dnsServers"},
            new String[] {"dnsIps", "dnsServers"},
            new String[] {"extraHostsString", "extraHosts"},
            new String[] {"genericResourcesString", "genericResources"},
            new String[] {"portBinds", "portBindings"},
            new String[] {"portBindingsString", "portBindings"});

    @Test
    @ConfiguredWithCode("simple-config.yaml")
    void exportContainsNoDuplicateAliasKeysPerTemplate(JenkinsConfiguredWithCodeRule j) throws Exception {
        String yaml = exportAsYaml();
        Set<String> templateKeys = parseTemplateKeys(yaml);

        // Sanity check: the template node was actually located and non-trivial.
        // Without this, an empty key set would make the alias assertions vacuously true.
        assertFalse(templateKeys.isEmpty(),
                "Failed to locate template keys in exported YAML — fixture probably changed.\n" + yaml);

        for (String[] pair : ALIAS_PAIRS) {
            boolean hasAlias = templateKeys.contains(pair[0]);
            boolean hasCanonical = templateKeys.contains(pair[1]);
            assertFalse(hasAlias && hasCanonical,
                    "Alias '" + pair[0] + "' and canonical '" + pair[1]
                            + "' both present in exported template — duplicate from aliased @DataBoundSetter.\n"
                            + yaml);
        }
    }

    @Test
    @ConfiguredWithCode("simple-config.yaml")
    void exportRoundTripsBackToSameConfiguration(JenkinsConfiguredWithCodeRule j) throws Exception {
        // Snapshot original template state.
        SwarmCloud before = (SwarmCloud) j.jenkins.clouds.get(0);
        SwarmAgentTemplate beforeTpl = before.getTemplates().get(0);
        String beforeLabel = beforeTpl.getLabelString();
        String beforeImage = beforeTpl.getImage();
        String beforeWorkingDir = beforeTpl.getRemoteFs();
        int beforeNumExecutors = beforeTpl.getNumExecutors();
        int beforeMaxInstances = beforeTpl.getMaxInstances();

        // Export → re-import.
        String exported = exportAsYaml();
        ConfigurationAsCode.get()
                .configureWith(new io.jenkins.plugins.casc.yaml.YamlSource<>(
                        new java.io.ByteArrayInputStream(exported.getBytes(StandardCharsets.UTF_8))));

        SwarmCloud after = (SwarmCloud) j.jenkins.clouds.get(0);
        assertEquals(1, after.getTemplates().size(), "template count must survive round-trip");
        SwarmAgentTemplate afterTpl = after.getTemplates().get(0);

        assertEquals(beforeLabel, afterTpl.getLabelString());
        assertEquals(beforeImage, afterTpl.getImage());
        assertEquals(beforeWorkingDir, afterTpl.getRemoteFs());
        assertEquals(beforeNumExecutors, afterTpl.getNumExecutors());
        assertEquals(beforeMaxInstances, afterTpl.getMaxInstances());
    }

    @Test
    void exportOfCloudWithoutTemplatesProducesOnlyName(JenkinsConfiguredWithCodeRule j) throws Exception {
        SwarmCloud cloud = new SwarmCloud("no-templates");
        cloud.setDockerHost("tcp://swarm:2376");
        j.jenkins.clouds.add(cloud);

        String yaml = exportAsYaml();
        // The cloud entry must be present even when templates are empty (the original complaint).
        assertTrue(yaml.contains("name: \"no-templates\"") || yaml.contains("name: no-templates"),
                "cloud without templates must still be exported. Got:\n" + yaml);
    }

    private static String exportAsYaml() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * Parses the exported YAML and returns the set of keys present on the first
     * {@code swarmAgentsCloud} template, regardless of indentation. Using a real YAML parser
     * here avoids the brittleness of counting raw indent levels: if CasC changes its
     * indent / flow style, the assertions still mean what they say.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> parseTemplateKeys(String yaml) {
        Object parsed = new Yaml().load(yaml);
        Map<String, Object> root = (Map<String, Object>) parsed;
        if (root == null) return Collections.emptySet();
        Map<String, Object> jenkins = (Map<String, Object>) root.get("jenkins");
        if (jenkins == null) return Collections.emptySet();
        List<Object> clouds = (List<Object>) jenkins.get("clouds");
        if (clouds == null || clouds.isEmpty()) return Collections.emptySet();
        Map<String, Object> first = (Map<String, Object>) clouds.get(0);
        Map<String, Object> swarmCloud = (Map<String, Object>) first.get("swarmAgentsCloud");
        if (swarmCloud == null) return Collections.emptySet();
        List<Object> templates = (List<Object>) swarmCloud.get("templates");
        if (templates == null || templates.isEmpty()) return Collections.emptySet();
        Map<String, Object> tpl = (Map<String, Object>) templates.get(0);
        return new LinkedHashSet<>(tpl.keySet());
    }
}
