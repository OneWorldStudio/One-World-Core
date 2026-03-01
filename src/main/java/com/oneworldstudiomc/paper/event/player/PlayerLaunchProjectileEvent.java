package com.oneworldstudiomc.paper.event.player;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player launches a projectile.
 * <p>
 * This is a Paper-compatible API event surface used by plugins that expect
 * Paper projectile launch hooks.
 */
public class PlayerLaunchProjectileEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final @NotNull Projectile projectile;
    private final @Nullable ItemStack itemStack;
    private boolean shouldConsume;
    private boolean cancelled;

    public PlayerLaunchProjectileEvent(@NotNull final Player player, @NotNull final Projectile projectile) {
        this(player, projectile, null, true);
    }

    public PlayerLaunchProjectileEvent(
            @NotNull final Player player,
            @NotNull final Projectile projectile,
            @Nullable final ItemStack itemStack
    ) {
        this(player, projectile, itemStack, true);
    }

    public PlayerLaunchProjectileEvent(
            @NotNull final Player player,
            @NotNull final Projectile projectile,
            @Nullable final ItemStack itemStack,
            final boolean shouldConsume
    ) {
        super(player);
        this.projectile = projectile;
        this.itemStack = itemStack;
        this.shouldConsume = shouldConsume;
    }

    public @NotNull Projectile getProjectile() {
        return this.projectile;
    }

    public @Nullable ItemStack getItemStack() {
        return this.itemStack;
    }

    public boolean shouldConsume() {
        return this.shouldConsume;
    }

    public void setShouldConsume(final boolean shouldConsume) {
        this.shouldConsume = shouldConsume;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
