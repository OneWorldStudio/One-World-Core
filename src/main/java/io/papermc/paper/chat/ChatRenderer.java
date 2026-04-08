package io.papermc.paper.chat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ChatRenderer {

    @NotNull
    Component render(
            @NotNull Player source,
            @NotNull Component sourceDisplayName,
            @NotNull Component message,
            @NotNull Audience viewer
    );

    static @NotNull ChatRenderer defaultRenderer() {
        return (source, sourceDisplayName, message, viewer) -> sourceDisplayName.append(Component.text(": ")).append(message);
    }

    static @NotNull ChatRenderer viewerUnaware(@NotNull final ViewerUnaware renderer) {
        return (source, sourceDisplayName, message, viewer) -> renderer.render(source, sourceDisplayName, message);
    }

    @FunctionalInterface
    interface ViewerUnaware {
        @NotNull
        Component render(@NotNull Player source, @NotNull Component sourceDisplayName, @NotNull Component message);
    }
}
