package com.oneworldstudiomc.paper.network;

import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public final class ChannelInitializeListenerHolder {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Key, ChannelInitializeListener> LISTENERS = new ConcurrentHashMap<>();

    private ChannelInitializeListenerHolder() {
    }

    public static void addListener(@NotNull Key key, @NotNull ChannelInitializeListener listener) {
        LISTENERS.put(key, listener);
    }

    public static void removeListener(@NotNull Key key) {
        LISTENERS.remove(key);
    }

    public static void callListeners(@NotNull Channel channel) {
        for (Map.Entry<Key, ChannelInitializeListener> entry : LISTENERS.entrySet()) {
            try {
                entry.getValue().afterInitChannel(channel);
            } catch (Throwable throwable) {
                LOGGER.error("Error while running ChannelInitializeListener '{}'", entry.getKey(), throwable);
            }
        }
    }
}
