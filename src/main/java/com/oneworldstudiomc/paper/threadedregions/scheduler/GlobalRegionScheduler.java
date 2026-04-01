package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Minimal Paper-compatible global region scheduler.
 */
public interface GlobalRegionScheduler {

    void execute(@NotNull Plugin plugin, @NotNull Runnable task);

    @NotNull ScheduledTask run(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task);

    @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, long delayTicks);

    @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks);

    void cancelTasks(@NotNull Plugin plugin);
}
