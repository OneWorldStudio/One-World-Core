package io.papermc.paper.threadedregions.scheduler;

import com.oneworldstudiomc.paper.threadedregions.scheduler.BaseScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility holder for libraries that reflect the Folia async task type.
 */
public final class FoliaAsyncScheduler {

    private FoliaAsyncScheduler() {
    }

    public static class AsyncScheduledTask extends BaseScheduledTask implements ScheduledTask {

        public AsyncScheduledTask(@NotNull Plugin owningPlugin, boolean repeatingTask) {
            super(owningPlugin, repeatingTask);
        }
    }
}
