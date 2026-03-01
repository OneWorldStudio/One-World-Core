package io.papermc.paper.event.player;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility wrapper for Paper API package.
 */
public class AsyncChatDecorateEvent extends com.oneworldstudiomc.paper.event.player.AsyncChatDecorateEvent {
    public AsyncChatDecorateEvent(@NotNull final Player player, @NotNull final Component originalMessage) {
        super(player, originalMessage);
    }
}
