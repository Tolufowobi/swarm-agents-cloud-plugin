/*
 * Declarative Pipeline agent script for Docker Swarm.
 *
 * Wires the {@code swarmAgent} declarative directive (see SwarmDeclarativeAgent)
 * onto the existing {@code swarmAgent} scripted step (SwarmAgentStep).
 *
 * Flow:
 *   1. Build the argument map for the swarmAgent step from the declarative fields.
 *   2. Invoke the swarmAgent step to provision the Docker Swarm service / node.
 *   3. Run the pipeline body inside a {@code node(label)} block bound to the
 *      provisioned label, so all enclosed steps execute on the new agent.
 *   4. Honour the inherited "retries" semantics of RetryableDeclarativeAgent.
 */
package io.jenkins.plugins.swarmcloud.pipeline

import org.jenkinsci.plugins.pipeline.modeldefinition.agent.CheckoutScript
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript2
import org.jenkinsci.plugins.workflow.cps.CpsScript

class SwarmDeclarativeAgentScript extends DeclarativeAgentScript2<SwarmDeclarativeAgent> {

    SwarmDeclarativeAgentScript(CpsScript s, SwarmDeclarativeAgent a) {
        super(s, a)
    }

    @Override
    void run(Closure body) {
        String resolvedCloud = describable.resolveCloudName()
        if (!resolvedCloud) {
            // resolveCloudName() returns null when no SwarmCloud is configured at all OR when
            // multiple are configured and the directive did not pick one. Raise a clear error
            // here rather than letting SwarmAgentStep emit "Swarm cloud not found: null".
            throw new IllegalArgumentException(
                    "swarmAgent: cannot resolve Docker Swarm cloud. " +
                    "Either configure exactly one Swarm cloud or set 'cloud' explicitly in the directive.")
        }

        Map<String, Object> args = [cloud: resolvedCloud]
        if (describable.template) {
            args.template = describable.template
        }
        if (describable.image) {
            args.image = describable.image
        }
        if (describable.label) {
            args.label = describable.label
        }
        if (describable.numExecutors > 0) {
            args.numExecutors = describable.numExecutors
        }
        if (describable.cpuLimit) {
            args.cpuLimit = describable.cpuLimit
        }
        if (describable.memoryLimit) {
            args.memoryLimit = describable.memoryLimit
        }
        if (describable.idleTimeout > 0) {
            args.idleTimeout = describable.idleTimeout
        }
        if (describable.connectionTimeout > 0) {
            args.connectionTimeout = describable.connectionTimeout
        }

        String labelExpr = describable.resolveLabel()
        Closure agentBody = {
            if (labelExpr) {
                script.node(labelExpr) {
                    CheckoutScript.doCheckout2(script, describable, describable.customWorkspace, body)
                }
            } else {
                CheckoutScript.doCheckout2(script, describable, describable.customWorkspace, body)
            }
        }

        if (describable.retries > 1) {
            script.retry(count: describable.retries, conditions: [script.agent(), script.nonresumable()]) {
                script.swarmAgent(args, agentBody)
            }
        } else {
            script.swarmAgent(args, agentBody)
        }
    }
}
