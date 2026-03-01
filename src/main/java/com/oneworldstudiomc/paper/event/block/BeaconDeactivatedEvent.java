package com.oneworldstudiomc.paper.event.block;

import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Paper-compatible event fired when a beacon is deactivated.
 */
public class BeaconDeactivatedEvent extends BlockEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    public BeaconDeactivatedEvent(@NotNull final Block block) {
        super(block);
    }

    public @NotNull Beacon getBeacon() {
        return (Beacon) this.block.getState();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
