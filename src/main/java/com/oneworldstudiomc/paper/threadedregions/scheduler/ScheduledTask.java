package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal Folia/Paper scheduled task surface for plugin compatibility.
 */
public interface ScheduledTask {

    enum ExecutionState {
        IDLE,
        RUNNING,
        CANCELLED_RUNNING,
        CANCELLED,
        FINISHED
    }

    enum CancelledState {
        CANCELLED_BY_CALLER,
        NEXT_RUNS_CANCELLED,
        ALREADY_CANCELLED,
        ALREADY_EXECUTED
    }

    @NotNull Plugin getOwningPlugin();

    boolean isRepeatingTask();

    @NotNull ExecutionState getExecutionState();

    @NotNull CancelledState cancel();

    boolean isCancelled();
}
