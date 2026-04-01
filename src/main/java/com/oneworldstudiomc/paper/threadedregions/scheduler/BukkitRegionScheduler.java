package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Main-thread backed implementation of the Paper region scheduler.
 */
public final class BukkitRegionScheduler implements io.papermc.paper.threadedregions.scheduler.RegionScheduler {

    @Override
    public void execute(@NotNull Plugin plugin, @NotNull Location location, @NotNull Runnable task) {
        validateLocation(location);
        Bukkit.getScheduler().runTask(Objects.requireNonNull(plugin, "plugin"), Objects.requireNonNull(task, "task"));
    }

    @Override
    public @NotNull ScheduledTask run(@NotNull Plugin plugin, @NotNull Location location, @NotNull Consumer<ScheduledTask> task) {
        validateLocation(location);
        return this.schedule(plugin, task, 0L, -1L);
    }

    @Override
    public @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Location location, @NotNull Consumer<ScheduledTask> task, long delayTicks) {
        validateLocation(location);
        return this.schedule(plugin, task, Math.max(0L, delayTicks), -1L);
    }

    @Override
    public @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Location location, @NotNull Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
        validateLocation(location);
        return this.schedule(plugin, task, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
    }

    private @NotNull ScheduledTask schedule(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> consumer, long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(consumer, "task");

        BukkitScheduledTask task = new BukkitScheduledTask(plugin, periodTicks > 0L);
        Runnable runnable = () -> task.executeTask(consumer);
        BukkitTask handle;
        if (periodTicks > 0L) {
            handle = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        } else if (delayTicks > 0L) {
            handle = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        } else {
            handle = Bukkit.getScheduler().runTask(plugin, runnable);
        }
        task.bind(handle);
        return task;
    }

    private static void validateLocation(@NotNull Location location) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
    }
}
