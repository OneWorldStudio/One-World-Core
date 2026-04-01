package com.oneworldstudiomc.paper.threadedregions.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Shared Bukkit-backed task implementation for Folia compatibility wrappers.
 */
public abstract class BaseScheduledTask implements ScheduledTask {

    private final Plugin owningPlugin;
    private final boolean repeatingTask;
    private final AtomicReference<ExecutionState> state = new AtomicReference<>(ExecutionState.IDLE);
    private volatile BukkitTask bukkitTask;

    protected BaseScheduledTask(@NotNull Plugin owningPlugin, boolean repeatingTask) {
        this.owningPlugin = owningPlugin;
        this.repeatingTask = repeatingTask;
    }

    public final void bind(@NotNull BukkitTask bukkitTask) {
        this.bukkitTask = bukkitTask;
    }

    protected final void executeTask(@NotNull Consumer<ScheduledTask> consumer) {
        if (this.isCancelled()) {
            return;
        }

        this.state.set(ExecutionState.RUNNING);
        try {
            consumer.accept(this);
        } finally {
            if (this.bukkitTask != null && this.bukkitTask.isCancelled()) {
                this.state.set(this.repeatingTask ? ExecutionState.CANCELLED_RUNNING : ExecutionState.CANCELLED);
            } else if (this.repeatingTask) {
                this.state.compareAndSet(ExecutionState.RUNNING, ExecutionState.IDLE);
            } else {
                this.state.compareAndSet(ExecutionState.RUNNING, ExecutionState.FINISHED);
            }
        }
    }

    protected final void markRetired() {
        BukkitTask task = this.bukkitTask;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        this.state.set(this.repeatingTask ? ExecutionState.CANCELLED : ExecutionState.FINISHED);
    }

    @Override
    public final @NotNull Plugin getOwningPlugin() {
        return this.owningPlugin;
    }

    @Override
    public final boolean isRepeatingTask() {
        return this.repeatingTask;
    }

    @Override
    public final @NotNull ExecutionState getExecutionState() {
        BukkitTask task = this.bukkitTask;
        if (task != null && task.isCancelled() && this.state.get() == ExecutionState.IDLE) {
            return ExecutionState.CANCELLED;
        }
        return this.state.get();
    }

    @Override
    public @NotNull CancelledState cancel() {
        ExecutionState currentState = this.state.get();
        if (currentState == ExecutionState.FINISHED) {
            return CancelledState.ALREADY_EXECUTED;
        }
        if (currentState == ExecutionState.CANCELLED || currentState == ExecutionState.CANCELLED_RUNNING) {
            return CancelledState.ALREADY_CANCELLED;
        }

        BukkitTask task = this.bukkitTask;
        if (task != null && task.isCancelled()) {
            this.state.set(ExecutionState.CANCELLED);
            return CancelledState.ALREADY_CANCELLED;
        }

        if (task != null) {
            task.cancel();
        }

        if (currentState == ExecutionState.RUNNING && this.repeatingTask) {
            this.state.set(ExecutionState.CANCELLED_RUNNING);
            return CancelledState.NEXT_RUNS_CANCELLED;
        }

        this.state.set(ExecutionState.CANCELLED);
        return CancelledState.CANCELLED_BY_CALLER;
    }

    @Override
    public final boolean isCancelled() {
        BukkitTask task = this.bukkitTask;
        return task != null ? task.isCancelled() : this.state.get() == ExecutionState.CANCELLED || this.state.get() == ExecutionState.CANCELLED_RUNNING;
    }
}
