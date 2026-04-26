package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudSlaveRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retention strategy for ephemeral one-shot Swarm agents (#7).
 * <p>
 * After the first completed build, the agent is removed and its underlying
 * Docker Swarm service is destroyed. Used when {@code template.oneShot}
 * is {@code true}. Modeled after the equivalent helper in the Jenkins
 * Kubernetes plugin — there is no {@code OnceRetentionStrategy} in
 * Jenkins core to reuse.
 */
public class SwarmOnceRetentionStrategy extends CloudSlaveRetentionStrategy<AbstractCloudComputer<?>>
        implements ExecutorListener {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(SwarmOnceRetentionStrategy.class.getName());

    private final int idleMinutes;

    @DataBoundConstructor
    public SwarmOnceRetentionStrategy(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        // No-op until the task completes; termination is triggered in taskCompleted.
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task,
                                          long durationMS, Throwable problems) {
        done(executor);
    }

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE",
            justification = "Termination is fire-and-forget. The returned Future is intentionally "
                    + "discarded — exceptions thrown by the worker are logged inside the lambda "
                    + "and there is no caller to consume the result.")
    private void done(@NonNull Executor executor) {
        final Computer owner = executor.getOwner();
        if (!(owner instanceof AbstractCloudComputer)) {
            LOGGER.log(Level.WARNING,
                    "One-shot retention strategy attached to non-cloud computer: {0}", owner);
            return;
        }
        final AbstractCloudComputer<?> c = (AbstractCloudComputer<?>) owner;
        c.setAcceptingTasks(false);
        Computer.threadPoolForRemoting.submit(() -> {
            try {
                AbstractCloudSlave node = (AbstractCloudSlave) c.getNode();
                if (node != null) {
                    node.terminate();
                    LOGGER.log(Level.FINE, "One-shot agent terminated: {0}", c.getName());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Interrupted while terminating one-shot agent", e);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to terminate one-shot agent", e);
            }
        });
    }

    @Extension
    @Symbol("swarmOnce")
    public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Swarm Agent One-Shot Retention Strategy";
        }
    }
}
