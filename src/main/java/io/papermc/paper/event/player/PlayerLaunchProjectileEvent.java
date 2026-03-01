package io.papermc.paper.event.player;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility wrapper for Paper API package.
 */
public class PlayerLaunchProjectileEvent extends com.oneworldstudiomc.paper.event.player.PlayerLaunchProjectileEvent {
    public PlayerLaunchProjectileEvent(@NotNull final Player player, @NotNull final Projectile projectile) {
        super(player, projectile);
    }

    public PlayerLaunchProjectileEvent(
            @NotNull final Player player,
            @NotNull final Projectile projectile,
            @Nullable final ItemStack itemStack
    ) {
        super(player, projectile, itemStack);
    }

    public PlayerLaunchProjectileEvent(
            @NotNull final Player player,
            @NotNull final Projectile projectile,
            @Nullable final ItemStack itemStack,
            final boolean shouldConsume
    ) {
        super(player, projectile, itemStack, shouldConsume);
    }
}
