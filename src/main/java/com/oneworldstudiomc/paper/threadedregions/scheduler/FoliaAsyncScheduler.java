package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility holder for Paper async scheduled task type names.
 */
public final class FoliaAsyncScheduler {

    private FoliaAsyncScheduler() {
    }

    public static final class AsyncScheduledTask extends io.papermc.paper.threadedregions.scheduler.FoliaAsyncScheduler.AsyncScheduledTask implements ScheduledTask {

        public AsyncScheduledTask(@NotNull Plugin owningPlugin, boolean repeatingTask) {
            super(owningPlugin, repeatingTask);
        }
    }
}
