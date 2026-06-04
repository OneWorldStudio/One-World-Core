package com.oneworldstudiomc.util;

import com.oneworldstudiomc.ai.koukou.KouKou;
import io.papermc.paper.chat.ChatRenderer;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_20_R1.util.LazyPlayerSet;
import org.bukkit.craftbukkit.v1_20_R1.util.Waitable;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatPatchFix {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // CraftBukkit start - add method
    public static void chat(ServerGamePacketListenerImpl packetListener, String s, PlayerChatMessage original, boolean async) {
        if (s.isEmpty() || packetListener.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            return;
        }

        if (!async && s.startsWith("/")) {
            packetListener.handleCommand(s);
        } else if (packetListener.player.getChatVisibility() != ChatVisiblity.SYSTEM) {
            org.bukkit.entity.Player thisPlayer = packetListener.getCraftPlayer();
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(async, thisPlayer, s, new LazyPlayerSet(packetListener.server));
            String essentialsChatFormat = getEssentialsChatFormat(thisPlayer);
            if (essentialsChatFormat != null) {
                try {
                    event.setFormat(essentialsChatFormat);
                } catch (RuntimeException ignored) {
                }
            }
            String originalFormat = event.getFormat();
            String originalMessage = event.getMessage();
            Bukkit.getPluginManager().callEvent(event);
            KouKou.chat("<%s>: %s".formatted(thisPlayer.getName(), originalMessage));

            if (PlayerChatEvent.getHandlerList().getRegisteredListeners().length != 0) {
                final PlayerChatEvent queueEvent = new PlayerChatEvent(thisPlayer, event.getMessage(), event.getFormat(), event.getRecipients());
                queueEvent.setCancelled(event.isCancelled());

                class SyncChat extends Waitable<Object> {
                    @Override
                    protected Object evaluate() {
                        Bukkit.getPluginManager().callEvent(queueEvent);
                        if (queueEvent.isCancelled()) {
                            return null;
                        }
                        sendChatWithPaperCompat(
                                packetListener,
                                original,
                                queueEvent.getPlayer(),
                                queueEvent.getFormat(),
                                queueEvent.getMessage(),
                                queueEvent.getRecipients(),
                                ((LazyPlayerSet) queueEvent.getRecipients()).isLazy(),
                                originalFormat,
                                originalMessage
                        );
                        return null;
                    }
                }

                Waitable<Object> waitable = new SyncChat();
                if (async) {
                    packetListener.server.processQueue.add(waitable);
                } else {
                    waitable.run();
                }

                try {
                    waitable.get();
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    throw new RuntimeException("Exception processing chat event", e.getCause());
                }
            }

            if (event.isCancelled()) {
                return;
            }

            if (async) {
                Waitable<Object> waitable = new Waitable<>() {
                    @Override
                    protected Object evaluate() {
                        sendChatWithPaperCompat(
                                packetListener,
                                original,
                                event.getPlayer(),
                                event.getFormat(),
                                event.getMessage(),
                                event.getRecipients(),
                                ((LazyPlayerSet) event.getRecipients()).isLazy(),
                                originalFormat,
                                originalMessage
                        );
                        return null;
                    }
                };
                packetListener.server.processQueue.add(waitable);
                try {
                    waitable.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    throw new RuntimeException("Exception processing chat event", e.getCause());
                }
                return;
            }

            sendChatWithPaperCompat(
                    packetListener,
                    original,
                    event.getPlayer(),
                    event.getFormat(),
                    event.getMessage(),
                    event.getRecipients(),
                    ((LazyPlayerSet) event.getRecipients()).isLazy(),
                    originalFormat,
                    originalMessage
            );
        }
    }
    // CraftBukkit end

    private static void sendChatWithPaperCompat(
            ServerGamePacketListenerImpl packetListener,
            PlayerChatMessage original,
            org.bukkit.entity.Player source,
            String format,
            String message,
            Set<org.bukkit.entity.Player> recipients,
            boolean lazyRecipients,
            String originalFormat,
            String originalMessage
    ) {
        boolean hasPaperListeners = io.papermc.paper.event.player.AsyncChatEvent.getHandlerList().getRegisteredListeners().length != 0;
        String essentialsChatFormat = getEssentialsChatFormat(source);
        if (!hasPaperListeners
                && lazyRecipients
                && !org.spigotmc.SpigotConfig.bungee
                && essentialsChatFormat == null
                && originalFormat.equals(format)
                && originalMessage.equals(message)
                && source.getName().equalsIgnoreCase(source.getDisplayName())) {
            packetListener.server.getPlayerList().broadcastChatMessage(original, packetListener.player, ChatType.bind(ChatType.CHAT, packetListener.player));
            return;
        }

        Set<Audience> viewers = new LinkedHashSet<>();
        if (lazyRecipients) {
            for (ServerPlayer recipient : packetListener.server.getPlayerList().players) {
                viewers.add(recipient.getBukkitEntity());
            }
        } else {
            viewers.addAll(recipients);
        }

        if (!originalFormat.equals(format) || !originalMessage.equals(message)) {
            sendLegacyFormattedChat(source, format, message, viewers);
            return;
        }

        String effectiveFormat = essentialsChatFormat == null ? format : essentialsChatFormat;
        Component sourceDisplayName = LEGACY.deserialize(source.getDisplayName());
        Component messageComponent = LEGACY.deserialize(message);
        io.papermc.paper.event.player.AsyncChatEvent paperEvent = new io.papermc.paper.event.player.AsyncChatEvent(
                false,
                source,
                viewers,
                ChatRenderer.viewerUnaware((viewerSource, viewerDisplayName, viewerMessage) -> LEGACY.deserialize(
                        renderLegacyChatFormat(
                                effectiveFormat,
                                viewerSource.getName(),
                                LEGACY.serialize(viewerDisplayName),
                                LEGACY.serialize(viewerMessage),
                                viewerSource.getWorld().getName()
                        )
                )),
                messageComponent,
                messageComponent,
                null
        );

        Bukkit.getPluginManager().callEvent(paperEvent);
        if (paperEvent.isCancelled()) {
            return;
        }

        for (Audience viewer : paperEvent.viewers()) {
            viewer.sendMessage(paperEvent.renderer().render(source, sourceDisplayName, paperEvent.message(), viewer));
        }

        Audience console = Bukkit.getConsoleSender();
        console.sendMessage(paperEvent.renderer().render(source, sourceDisplayName, paperEvent.message(), console));
    }

    private static void sendLegacyFormattedChat(
            org.bukkit.entity.Player source,
            String format,
            String message,
            Set<Audience> viewers
    ) {
        Component formatted = LEGACY.deserialize(renderLegacyChatFormat(format, source.getName(), source.getDisplayName(), message, source.getWorld().getName()));
        for (Audience viewer : viewers) {
            viewer.sendMessage(formatted);
        }
        Bukkit.getConsoleSender().sendMessage(formatted);
    }

    private static String renderLegacyChatFormat(String format, String playerName, String displayName, String message, String worldName) {
        String rendered;
        if (format.indexOf('{') != -1) {
            rendered = format
                    .replace("{DISPLAYNAME}", displayName)
                    .replace("{NICKNAME}", displayName)
                    .replace("{USERNAME}", playerName)
                    .replace("{PLAYER}", playerName)
                    .replace("{NAME}", playerName)
                    .replace("{MESSAGE}", message)
                    .replace("{WORLDNAME}", worldName)
                    .replace("{WORLD}", worldName)
                    .replace("{GROUP}", "")
                    .replace("{PREFIX}", "")
                    .replace("{SUFFIX}", "");
        } else {
            try {
                rendered = String.format(format, displayName, message);
            } catch (RuntimeException ignored) {
                rendered = displayName + ": " + message;
            }
        }
        return ChatColor.translateAlternateColorCodes('&', rendered);
    }

    private static String getEssentialsChatFormat(org.bukkit.entity.Player player) {
        Plugin essentialsChat = Bukkit.getPluginManager().getPlugin("EssentialsChat");
        if (essentialsChat == null) {
            essentialsChat = Bukkit.getPluginManager().getPlugin("EssentialsXChat");
        }
        if (essentialsChat == null || !essentialsChat.isEnabled()) {
            return null;
        }

        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        String format = null;
        if (essentials instanceof JavaPlugin javaPlugin && javaPlugin.isEnabled()) {
            format = readEssentialsChatFormat(javaPlugin.getConfig(), player);
            if (format == null) {
                format = readEssentialsChatFormat(loadPluginConfig(javaPlugin), player);
            }
        }
        if (format == null && essentialsChat instanceof JavaPlugin javaPlugin) {
            format = readEssentialsChatFormat(javaPlugin.getConfig(), player);
            if (format == null) {
                format = readEssentialsChatFormat(loadPluginConfig(javaPlugin), player);
            }
        }
        return format;
    }

    private static FileConfiguration loadPluginConfig(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        return configFile.isFile() ? YamlConfiguration.loadConfiguration(configFile) : null;
    }

    private static String readEssentialsChatFormat(FileConfiguration config, org.bukkit.entity.Player player) {
        if (config == null) {
            return null;
        }

        String format = getGroupFormat(config, player);
        if (format == null) {
            format = config.getString("chat.format");
        }
        if (format == null || format.isBlank()) {
            format = config.getString("format");
        }
        return format == null || format.isBlank() ? null : format;
    }

    private static String getGroupFormat(FileConfiguration config, org.bukkit.entity.Player player) {
        ConfigurationSection groupFormats = config.getConfigurationSection("chat.group-formats");
        if (groupFormats == null) {
            groupFormats = config.getConfigurationSection("group-formats");
        }
        if (groupFormats == null) {
            return null;
        }

        for (String group : groupFormats.getKeys(false)) {
            String format = groupFormats.getString(group);
            if (format != null && !format.isBlank() && player.hasPermission("essentials.chat.group." + group)) {
                return format;
            }
        }
        return null;
    }
}
