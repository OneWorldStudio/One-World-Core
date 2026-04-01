package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit-backed scheduled task used for sync region compatibility.
 */
public class BukkitScheduledTask extends BaseScheduledTask implements io.papermc.paper.threadedregions.scheduler.ScheduledTask {

    public BukkitScheduledTask(@NotNull Plugin owningPlugin, boolean repeatingTask) {
        super(owningPlugin, repeatingTask);
    }
}
