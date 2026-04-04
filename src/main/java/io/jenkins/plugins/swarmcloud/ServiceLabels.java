package io.jenkins.plugins.swarmcloud;

/**
 * Constants for Docker Swarm service labels used to identify and manage Jenkins agent services.
 */
public final class ServiceLabels {

    private ServiceLabels() {
        // Utility class
    }

    /** Marks a Docker Swarm service as a Jenkins agent. Value: "true" */
    public static final String AGENT = "jenkins.agent";

    /** Stores the Jenkins agent name. */
    public static final String AGENT_NAME = "jenkins.agent.name";

    /** Stores the template name used to create the agent. */
    public static final String TEMPLATE = "jenkins.template";

    /** Identifies the Jenkins cloud that owns the service. */
    public static final String CLOUD = "jenkins.cloud";

    /** Timestamp (millis) when the service was created. */
    public static final String CREATED = "jenkins.created";

    /** The cloud name value used in CLOUD label. */
    public static final String CLOUD_NAME = "swarm-agents-cloud";
}
