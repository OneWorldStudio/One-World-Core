package com.oneworldstudiomc.paper.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Paper compatibility event fired when a player movement check fails.
 */
public class PlayerFailMoveEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();
    private final FailReason failReason;
    private final Location from;
    private final Location to;
    private boolean allowed;
    private boolean logWarning;

    public PlayerFailMoveEvent(
            @NotNull final Player player,
            @NotNull final FailReason failReason,
            final boolean allowed,
            final boolean logWarning,
            @NotNull final Location from,
            @NotNull final Location to
    ) {
        super(player);
        this.failReason = failReason;
        this.allowed = allowed;
        this.logWarning = logWarning;
        this.from = from;
        this.to = to;
    }

    public PlayerFailMoveEvent(
            @NotNull final Player player,
            @NotNull final FailReason failReason,
            @NotNull final Location to,
            final boolean allowed
    ) {
        this(player, failReason, allowed, true, player.getLocation(), to);
    }

    public @NotNull FailReason getFailReason() {
        return this.failReason;
    }

    public @NotNull Location getFrom() {
        return this.from;
    }

    public @NotNull Location getTo() {
        return this.to;
    }

    public boolean isAllowed() {
        return this.allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean getLogWarning() {
        return this.logWarning;
    }

    public void setLogWarning(final boolean logWarning) {
        this.logWarning = logWarning;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }

    public enum FailReason {
        MOVED_TOO_QUICKLY,
        MOVED_WRONGLY,
        MISMATCHED_MOVEMENT,
        CLIPPED_INTO_BLOCK,
        MOVED_INTO_UNLOADED_CHUNK
    }
}
