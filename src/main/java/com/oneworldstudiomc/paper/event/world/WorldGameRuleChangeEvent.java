package com.oneworldstudiomc.paper.event.world;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Paper compatibility event fired when a gamerule value changes.
 */
public class WorldGameRuleChangeEvent extends WorldEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final @Nullable CommandSender commandSender;
    private final GameRule<?> gameRule;
    private String value;
    private boolean cancelled;

    public WorldGameRuleChangeEvent(
            @NotNull final World world,
            @Nullable final CommandSender commandSender,
            @NotNull final GameRule<?> gameRule,
            @NotNull final String value
    ) {
        super(world);
        this.commandSender = commandSender;
        this.gameRule = gameRule;
        this.value = value;
    }

    public WorldGameRuleChangeEvent(@NotNull final World world, @NotNull final GameRule<?> gameRule, @NotNull final String value) {
        this(world, null, gameRule, value);
    }

    public @Nullable CommandSender getCommandSender() {
        return this.commandSender;
    }

    public @NotNull GameRule<?> getGameRule() {
        return this.gameRule;
    }

    public @NotNull String getValue() {
        return this.value;
    }

    public void setValue(@NotNull final String value) {
        this.value = value;
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
