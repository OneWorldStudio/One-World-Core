package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Minimal Paper-compatible region scheduler.
 */
public interface RegionScheduler {

    void execute(@NotNull Plugin plugin, @NotNull Location location, @NotNull Runnable task);

    @NotNull ScheduledTask run(@NotNull Plugin plugin, @NotNull Location location, @NotNull Consumer<ScheduledTask> task);

    @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Location location, @NotNull Consumer<ScheduledTask> task, long delayTicks);

    @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Location location, @NotNull Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks);

    default void execute(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ, @NotNull Runnable task) {
        this.execute(plugin, new Location(world, chunkX << 4, 0.0D, chunkZ << 4), task);
    }

    default @NotNull ScheduledTask run(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ, @NotNull Consumer<ScheduledTask> task) {
        return this.run(plugin, new Location(world, chunkX << 4, 0.0D, chunkZ << 4), task);
    }

    default @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ, @NotNull Consumer<ScheduledTask> task, long delayTicks) {
        return this.runDelayed(plugin, new Location(world, chunkX << 4, 0.0D, chunkZ << 4), task, delayTicks);
    }

    default @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull World world, int chunkX, int chunkZ, @NotNull Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
        return this.runAtFixedRate(plugin, new Location(world, chunkX << 4, 0.0D, chunkZ << 4), task, initialDelayTicks, periodTicks);
    }
}
