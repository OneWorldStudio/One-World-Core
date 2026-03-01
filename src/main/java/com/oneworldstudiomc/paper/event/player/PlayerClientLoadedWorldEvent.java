package com.oneworldstudiomc.paper.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility event used by plugins that wait for the client world to finish loading.
 */
public class PlayerClientLoadedWorldEvent extends PlayerEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    public PlayerClientLoadedWorldEvent(@NotNull final Player player) {
        super(player);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
