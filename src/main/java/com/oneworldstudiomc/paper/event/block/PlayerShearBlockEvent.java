package com.oneworldstudiomc.paper.event.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerShearBlockEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final @NotNull Block block;
    private final @Nullable ItemStack itemStack;
    private final @NotNull EquipmentSlot hand;
    private boolean cancelled;

    public PlayerShearBlockEvent(@NotNull Player player, @NotNull Block block, @Nullable ItemStack itemStack, @NotNull EquipmentSlot hand) {
        super(player);
        this.block = block;
        this.itemStack = itemStack;
        this.hand = hand;
    }

    public @NotNull Block getBlock() {
        return this.block;
    }

    public @Nullable ItemStack getItemStack() {
        return this.itemStack;
    }

    public @NotNull EquipmentSlot getHand() {
        return this.hand;
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
