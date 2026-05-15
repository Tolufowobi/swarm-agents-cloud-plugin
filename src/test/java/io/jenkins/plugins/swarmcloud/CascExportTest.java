package io.jenkins.plugins.swarmcloud;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        String swarmBlock = extractSwarmAgentsCloudBlock(yaml);
        Map<String, Integer> keyCount = countKeysAtIndent(swarmBlock, "            ");

        for (String[] pair : ALIAS_PAIRS) {
            int alias = keyCount.getOrDefault(pair[0], 0);
            int canonical = keyCount.getOrDefault(pair[1], 0);
            assertFalse(alias > 0 && canonical > 0,
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

    /** Extracts the lines from {@code - swarmAgentsCloud:} up to the next top-level dash, inclusive. */
    private static String extractSwarmAgentsCloudBlock(String yaml) {
        int start = yaml.indexOf("- swarmAgentsCloud:");
        if (start < 0) {
            fail("swarmAgentsCloud entry not found in export:\n" + yaml);
        }
        int end = yaml.indexOf("\n  - ", start + 1);
        if (end < 0) {
            end = yaml.length();
        }
        return yaml.substring(start, end);
    }

    /**
     * Counts how many times each YAML key appears at the given indent level inside the given block.
     * Used to detect duplicated alias attributes side-by-side at the template indentation.
     */
    private static Map<String, Integer> countKeysAtIndent(String block, String indent) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String line : block.split("\n")) {
            if (!line.startsWith(indent)) continue;
            String rest = line.substring(indent.length());
            if (rest.isEmpty() || rest.charAt(0) == ' ' || rest.charAt(0) == '-') continue;
            int colon = rest.indexOf(':');
            if (colon <= 0) continue;
            String key = rest.substring(0, colon).trim();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }
}
