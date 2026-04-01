package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Bukkit async scheduler adapter for Folia/Paper compatibility.
 */
public final class BukkitAsyncScheduler implements io.papermc.paper.threadedregions.scheduler.AsyncScheduler {

    @Override
    public @NotNull ScheduledTask runNow(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task) {
        return this.schedule(plugin, task, 0L, -1L);
    }

    @Override
    public @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, long delay, @NotNull TimeUnit unit) {
        return this.schedule(plugin, task, toTicks(delay, unit), -1L);
    }

    @Override
    public @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, long initialDelay, long period, @NotNull TimeUnit unit) {
        return this.schedule(plugin, task, toTicks(initialDelay, unit), Math.max(1L, toTicks(period, unit)));
    }

    @Override
    public void cancelTasks(@NotNull Plugin plugin) {
        Bukkit.getScheduler().cancelTasks(Objects.requireNonNull(plugin, "plugin"));
    }

    private @NotNull ScheduledTask schedule(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> consumer, long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(consumer, "task");

        FoliaAsyncScheduler.AsyncScheduledTask task = new FoliaAsyncScheduler.AsyncScheduledTask(plugin, periodTicks > 0L);
        Runnable runnable = () -> task.executeTask(consumer);
        BukkitTask handle;
        if (periodTicks > 0L) {
            handle = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
        } else if (delayTicks > 0L) {
            handle = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks);
        } else {
            handle = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
        task.bind(handle);
        return task;
    }

    private static long toTicks(long value, @NotNull TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        return Math.max(0L, Math.max(1L, unit.toMillis(value) / 50L));
    }
}
