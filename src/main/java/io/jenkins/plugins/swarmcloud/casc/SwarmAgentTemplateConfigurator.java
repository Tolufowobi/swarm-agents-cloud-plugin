package io.jenkins.plugins.swarmcloud.casc;

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.impl.configurators.DataBoundConfigurator;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import org.jenkinsci.plugins.variant.OptionalExtension;

import java.util.Set;

/**
 * Custom CasC {@link io.jenkins.plugins.casc.Configurator} for {@link SwarmAgentTemplate}.
 *
 * <p>Fixes two CasC export problems caused by the docker-swarm-plugin compatibility surface:</p>
 *
 * <ol>
 *   <li><b>Infinite recursion / {@code StackOverflowError}.</b> {@code SwarmAgentTemplate} carries a
 *       back-reference to its owning {@link io.jenkins.plugins.swarmcloud.SwarmCloud} via public
 *       {@code setParent(SwarmCloud)} / {@code getParent()}. CasC's introspection picks up that
 *       getter/setter pair as an exportable attribute and recurses
 *       {@code cloud → templates → template → parent → cloud → ...}. We exclude it from
 *       introspection here so the cycle never starts.</li>
 *   <li><b>Duplicate keys for alias setters.</b> {@code SwarmAgentTemplate} exposes many
 *       parallel {@code @DataBoundSetter}s ({@code label}/{@code labelString},
 *       {@code workingDir}/{@code remoteFs}, {@code limitsNanoCPUs}/{@code cpuLimit},
 *       {@code *String} variants, etc.) so that YAML written for the legacy docker-swarm-plugin
 *       still imports cleanly. Each setter creates its own CasC {@code Attribute}, which
 *       produced duplicated sibling keys on export. We strip the alias keys after the underlying
 *       configurator has computed the export mapping — the canonical key still carries the
 *       value, and the alias setters remain wired for backwards-compatible import.</li>
 * </ol>
 */
@OptionalExtension(requirePlugins = "configuration-as-code")
public class SwarmAgentTemplateConfigurator extends DataBoundConfigurator<SwarmAgentTemplate> {

    /** Hides the parent back-reference from CasC introspection — see class javadoc. */
    private static final Set<String> EXCLUDED_INTROSPECTION = Set.of("parent");

    /**
     * Alias attribute names that CasC must accept during <em>import</em> but must omit from
     * <em>export</em>, because they duplicate a canonical attribute.
     */
    private static final Set<String> ALIAS_EXPORT_KEYS = Set.of(
            // Direct synonyms (same type as canonical)
            "label",                       // alias of labelString
            "workingDir",                  // alias of remoteFs
            "hostBinds",                   // alias of mounts
            "envVars",                     // alias of environmentVariables
            "dnsIps",                      // alias of dnsServersString → dnsServers
            // Type-converting compatibility aliases
            "limitsNanoCPUs",              // alias of cpuLimit (long↔string units)
            "limitsMemoryBytes",           // alias of memoryLimit (long↔string units)
            "reservationsNanoCPUs",        // alias of cpuReservation
            "reservationsMemoryBytes",     // alias of memoryReservation
            // List ↔ String UI aliases
            "placementConstraintsString",  // alias of placementConstraints
            "networkAliasesString",        // alias of networkAliases
            "configsString",               // alias of configs
            "cacheDirsString",             // alias of cacheDirs
            "capAddString",                // alias of capAdd
            "capDropString",               // alias of capDrop
            "sysctlsString",               // alias of sysctls
            "dnsServersString",            // alias of dnsServers
            "extraHostsString",            // alias of extraHosts
            "genericResourcesString",      // alias of genericResources
            "portBinds",                   // alias of portBindingsString → portBindings
            "portBindingsString");         // alias of portBindings

    public SwarmAgentTemplateConfigurator() {
        super(SwarmAgentTemplate.class);
    }

    @Override
    protected Set<String> exclusions() {
        return EXCLUDED_INTROSPECTION;
    }

    @Override
    public CNode describe(SwarmAgentTemplate instance, ConfigurationContext context) throws Exception {
        CNode described = super.describe(instance, context);
        if (described instanceof Mapping) {
            Mapping mapping = (Mapping) described;
            ALIAS_EXPORT_KEYS.forEach(mapping::remove);
        }
        return described;
    }
}
