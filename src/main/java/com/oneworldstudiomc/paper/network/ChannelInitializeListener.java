package com.oneworldstudiomc.paper.network;

import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ChannelInitializeListener {

    void afterInitChannel(@NotNull Channel channel);
}
