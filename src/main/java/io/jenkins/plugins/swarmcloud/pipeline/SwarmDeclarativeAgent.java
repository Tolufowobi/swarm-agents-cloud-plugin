package io.jenkins.plugins.swarmcloud.pipeline;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Util;
import hudson.model.Item;
import hudson.slaves.Cloud;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.RetryableDeclarativeAgent;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;

/**
 * Declarative Pipeline agent for Docker Swarm.
 *
 * <p>Usage:</p>
 * <pre>
 * pipeline {
 *     agent {
 *         swarmAgent {
 *             cloud 'docker-swarm'
 *             template 'maven'
 *             label 'maven java'
 *         }
 *     }
 *     stages { ... }
 * }
 * </pre>
 *
 * <p>Or with inline image configuration:</p>
 * <pre>
 * agent {
 *     swarmAgent {
 *         image 'jenkins/inbound-agent:alpine'
 *         label 'docker'
 *     }
 * }
 * </pre>
 *
 * <p>If exactly one {@link SwarmCloud} is configured, {@code cloud} may be omitted.</p>
 */
public class SwarmDeclarativeAgent extends RetryableDeclarativeAgent<SwarmDeclarativeAgent> {

    private static final long serialVersionUID = 1L;

    private String cloud;
    private String template;
    private String image;
    private String label;
    private int numExecutors;
    private String cpuLimit;
    private String memoryLimit;
    private int idleTimeout;
    private int connectionTimeout;
    private String customWorkspace;

    @DataBoundConstructor
    public SwarmDeclarativeAgent() {
        // All fields are optional; populated via @DataBoundSetter.
    }

    @Nullable
    public String getCloud() {
        return cloud;
    }

    @DataBoundSetter
    public void setCloud(String cloud) {
        this.cloud = Util.fixEmptyAndTrim(cloud);
    }

    @Nullable
    public String getTemplate() {
        return template;
    }

    @DataBoundSetter
    public void setTemplate(String template) {
        this.template = Util.fixEmptyAndTrim(template);
    }

    @Nullable
    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = Util.fixEmptyAndTrim(image);
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = Util.fixEmptyAndTrim(label);
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    @DataBoundSetter
    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
    }

    @Nullable
    public String getCpuLimit() {
        return cpuLimit;
    }

    @DataBoundSetter
    public void setCpuLimit(String cpuLimit) {
        this.cpuLimit = Util.fixEmptyAndTrim(cpuLimit);
    }

    @Nullable
    public String getMemoryLimit() {
        return memoryLimit;
    }

    @DataBoundSetter
    public void setMemoryLimit(String memoryLimit) {
        this.memoryLimit = Util.fixEmptyAndTrim(memoryLimit);
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    @DataBoundSetter
    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @DataBoundSetter
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @Nullable
    public String getCustomWorkspace() {
        return customWorkspace;
    }

    @DataBoundSetter
    public void setCustomWorkspace(String customWorkspace) {
        this.customWorkspace = Util.fixEmptyAndTrim(customWorkspace);
    }

    /**
     * Resolves the cloud name to use.
     * <p>Returns the explicit {@code cloud} value, or — if only one {@link SwarmCloud} is configured —
     * its name. Returns {@code null} when no cloud can be resolved deterministically; the underlying
     * step will then fail with a clear error.</p>
     */
    @Nullable
    public String resolveCloudName() {
        if (cloud != null && !cloud.isEmpty()) {
            return cloud;
        }
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }
        SwarmCloud single = null;
        for (Cloud c : jenkins.clouds) {
            if (c instanceof SwarmCloud) {
                if (single != null) {
                    return null; // ambiguous — multiple swarm clouds; require explicit cloud
                }
                single = (SwarmCloud) c;
            }
        }
        return single != null ? single.name : null;
    }

    /**
     * Resolves an effective node label for scheduling.
     * <p>Prefers explicit {@code label}, falls back to the referenced template's {@code labelString}.
     * Returns {@code null} if no label can be determined.</p>
     */
    @Nullable
    public String resolveLabel() {
        if (label != null && !label.isEmpty()) {
            return label;
        }
        String cloudName = resolveCloudName();
        if (cloudName == null || template == null || template.isEmpty()) {
            return null;
        }
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }
        for (Cloud c : jenkins.clouds) {
            if (c instanceof SwarmCloud && cloudName.equals(c.name)) {
                SwarmAgentTemplate tpl = ((SwarmCloud) c).getTemplateByName(template);
                if (tpl != null) {
                    return Util.fixEmptyAndTrim(tpl.getLabelString());
                }
            }
        }
        return null;
    }

    @OptionalExtension(requirePlugins = "pipeline-model-extensions")
    @Symbol("swarmAgent")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<SwarmDeclarativeAgent> {

        @Override
        @NonNull
        public String getDisplayName() {
            return "Docker Swarm Agent";
        }

        /**
         * Populates the "Cloud" dropdown in the declarative agent UI.
         *
         * <p>Annotated with {@link POST} and gated on {@link Item#READ} (or {@link Jenkins#READ} when
         * invoked outside a job context) to satisfy Jenkins security best practices: callers without
         * read access to the surrounding item get an empty dropdown rather than a glimpse at the
         * configured cloud names. See CodeQL rules {@code jenkins/csrf} and
         * {@code jenkins/no-permission-check}.</p>
         */
        @POST
        @SuppressWarnings("unused") // used by Jelly form
        public ListBoxModel doFillCloudItems(@AncestorInPath @Nullable Item item) {
            ListBoxModel items = new ListBoxModel();
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.READ);
            } else {
                item.checkPermission(Item.READ);
            }
            items.add("(auto)", "");
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null) {
                for (Cloud c : jenkins.clouds) {
                    if (c instanceof SwarmCloud) {
                        items.add(c.name);
                    }
                }
            }
            return items;
        }
    }
}
