package com.oneworldstudiomc.paper.event.block;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Paper-compatible event fired when a block breaks another block.
 */
public class BlockBreakBlockEvent extends BlockExpEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final @NotNull Block source;
    private final @NotNull List<ItemStack> drops;

    public BlockBreakBlockEvent(@NotNull final Block block, @NotNull final Block source, @NotNull final List<ItemStack> drops) {
        super(block, 0);
        this.source = source;
        this.drops = drops;
    }

    public @NotNull List<ItemStack> getDrops() {
        return this.drops;
    }

    public @NotNull Block getSource() {
        return this.source;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
