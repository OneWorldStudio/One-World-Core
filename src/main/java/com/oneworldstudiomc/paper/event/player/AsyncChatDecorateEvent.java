package com.oneworldstudiomc.paper.event.player;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal Paper-compatible async chat decorate event.
 */
public class AsyncChatDecorateEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Component originalMessage;
    private Component result;
    private boolean cancelled;

    public AsyncChatDecorateEvent(@NotNull final Player player, @NotNull final Component originalMessage) {
        super(player);
        this.originalMessage = originalMessage;
        this.result = originalMessage;
    }

    public @NotNull Player player() {
        return this.getPlayer();
    }

    public @NotNull Component originalMessage() {
        return this.originalMessage;
    }

    public void result(@NotNull final Component component) {
        this.result = component;
    }

    public @NotNull Component result() {
        return this.result;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
