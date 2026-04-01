package com.oneworldstudiomc.paper.event.player;

import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerPickupExperienceEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final @NotNull ExperienceOrb experienceOrb;
    private boolean cancelled;

    public PlayerPickupExperienceEvent(@NotNull Player player, @NotNull ExperienceOrb experienceOrb) {
        super(player);
        this.experienceOrb = experienceOrb;
    }

    public @NotNull ExperienceOrb getExperienceOrb() {
        return this.experienceOrb;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
