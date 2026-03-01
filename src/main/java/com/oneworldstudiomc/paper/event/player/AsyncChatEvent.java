package com.oneworldstudiomc.paper.event.player;

import com.oneworldstudiomc.paper.chat.ChatRenderer;
import java.util.Collections;
import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal Paper compatibility event marker.
 */
public class AsyncChatEvent extends AbstractChatEvent {

    private static final HandlerList handlers = new HandlerList();

    public AsyncChatEvent(@NotNull final Player who) {
        this(
                true,
                who,
                Collections.emptySet(),
                ChatRenderer.defaultRenderer(),
                Component.empty(),
                Component.empty(),
                null
        );
    }

    public AsyncChatEvent(
            final boolean async,
            @NotNull final Player player,
            @NotNull final Set<Audience> viewers,
            @NotNull final ChatRenderer renderer,
            @NotNull final Component message,
            @NotNull final Component originalMessage,
            @Nullable final SignedMessage signedMessage
    ) {
        super(async, player, viewers, renderer, message, originalMessage, signedMessage);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
