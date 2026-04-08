package io.papermc.paper.event.player;

import io.papermc.paper.chat.ChatRenderer;
import java.util.LinkedHashSet;
import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsyncChatEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Set<Audience> viewers;
    private final Component originalMessage;
    private final SignedMessage signedMessage;
    private ChatRenderer renderer;
    private Component message;
    private boolean cancelled;

    public AsyncChatEvent(@NotNull final Player who) {
        this(false, who, new LinkedHashSet<>(), ChatRenderer.defaultRenderer(), Component.empty(), Component.empty(), null);
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
        super(player);
        this.viewers = new LinkedHashSet<>(viewers);
        this.renderer = renderer;
        this.message = message;
        this.originalMessage = originalMessage;
        this.signedMessage = signedMessage;
    }

    public final @NotNull Set<Audience> viewers() {
        return this.viewers;
    }

    public final void renderer(@NotNull final ChatRenderer renderer) {
        this.renderer = renderer;
    }

    public final @NotNull ChatRenderer renderer() {
        return this.renderer;
    }

    public final @NotNull Component message() {
        return this.message;
    }

    public final void message(@NotNull final Component message) {
        this.message = message;
    }

    public final @NotNull Component originalMessage() {
        return this.originalMessage;
    }

    public final @Nullable SignedMessage signedMessage() {
        return this.signedMessage;
    }

    @Override
    public final boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public final void setCancelled(final boolean cancel) {
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
