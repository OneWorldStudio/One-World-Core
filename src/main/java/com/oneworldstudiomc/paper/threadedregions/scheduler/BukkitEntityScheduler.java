package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Main-thread entity scheduler compatibility wrapper.
 */
public final class BukkitEntityScheduler implements io.papermc.paper.threadedregions.scheduler.EntityScheduler {

    private final Entity entity;

    public BukkitEntityScheduler(@NotNull Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    @Override
    public @Nullable ScheduledTask run(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, @Nullable Runnable retired) {
        return this.schedule(plugin, task, retired, 0L, -1L);
    }

    @Override
    public boolean execute(@NotNull Plugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        if (!this.entity.isValid()) {
            if (retired != null) {
                retired.run();
            }
            return false;
        }

        Runnable runnable = () -> {
            if (!this.entity.isValid()) {
                if (retired != null) {
                    retired.run();
                }
                return;
            }
            task.run();
        };

        if (delayTicks > 0L) {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
        return true;
    }

    @Override
    public @Nullable ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, @Nullable Runnable retired, long delayTicks) {
        return this.schedule(plugin, task, retired, Math.max(0L, delayTicks), -1L);
    }

    @Override
    public @Nullable ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, @Nullable Runnable retired, long initialDelayTicks, long periodTicks) {
        return this.schedule(plugin, task, retired, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
    }

    private @Nullable ScheduledTask schedule(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> consumer, @Nullable Runnable retired, long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(consumer, "task");

        if (!this.entity.isValid()) {
            if (retired != null) {
                retired.run();
            }
            return null;
        }

        BukkitScheduledTask task = new BukkitScheduledTask(plugin, periodTicks > 0L);
        Runnable runnable = () -> {
            if (!this.entity.isValid()) {
                if (retired != null) {
                    retired.run();
                }
                task.markRetired();
                return;
            }
            task.executeTask(consumer);
        };

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
}
