package net.minecraft.server.network;

import com.oneworldstudiomc.api.event.MohistServerListPingEvent;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerStatusPacketListener;
import net.minecraft.network.protocol.status.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;

import java.util.Collections;
import java.util.Optional;

public class ServerStatusPacketListenerImpl implements ServerStatusPacketListener {
   private static final Component DISCONNECT_REASON = Component.translatable("multiplayer.status.request_handled");
   private final ServerStatus status;
   private final @org.jetbrains.annotations.Nullable String statusCache; // FORGE: cache status JSON
   private final Connection connection;
   private boolean hasRequestedStatus;

   public ServerStatusPacketListenerImpl(ServerStatus p_272864_, Connection p_273586_) {
      this(p_272864_, p_273586_, null);
   }
   public ServerStatusPacketListenerImpl(ServerStatus p_272864_, Connection p_273586_, @org.jetbrains.annotations.Nullable String statusCache) {
      this.status = p_272864_;
      this.connection = p_273586_;
      this.statusCache = statusCache;
   }

   public void onDisconnect(Component p_10091_) {
   }

   public boolean isAcceptingMessages() {
      return this.connection.isConnected();
   }

   public void handleStatusRequest(ServerboundStatusRequestPacket p_10095_) {
      if (this.hasRequestedStatus) {
         this.connection.disconnect(DISCONNECT_REASON);
      } else {
          this.hasRequestedStatus = true;
          MohistServerListPingEvent event = new MohistServerListPingEvent(connection, MinecraftServer.getServer());
          Bukkit.getPluginManager().callEvent(event);

          MinecraftServer server = MinecraftServer.getServer();
          final Object[] players = event.getPlayers();

          java.util.List<GameProfile> profiles = new java.util.ArrayList<GameProfile>(players.length);
          for (Object player : players) {
            if (player != null) {
               ServerPlayer entityPlayer = ((ServerPlayer) player);
               if (entityPlayer.allowsListing()) {
                  profiles.add(entityPlayer.getGameProfile());
               } else {
                  profiles.add(MinecraftServer.ANONYMOUS_PLAYER_PROFILE);
               }
            }
          }

         ServerStatus.Players playerSample = new ServerStatus.Players(event.getMaxPlayers(), (server.hidesOnlinePlayers()) ? 0 : profiles.size(), (server.hidesOnlinePlayers()) ? Collections.emptyList() : profiles);

         ServerStatus ping = new ServerStatus(
                 CraftChatMessage.fromString(event.getMotd(), true)[0],
                 Optional.of(playerSample),
                 Optional.of(new ServerStatus.Version(server.getServerModName() + " " + server.getServerVersion(), SharedConstants.getCurrentVersion().getProtocolVersion())),
                 (event.icon.value != null) ? Optional.of(new ServerStatus.Favicon(event.icon.value)) : Optional.empty(),
                 server.enforceSecureProfile(),
                 Optional.of(new net.minecraftforge.network.ServerStatusPing())
         );
         this.connection.send(new ClientboundStatusResponsePacket(ping));
         // CraftBukkit end
      }
   }

   public void handlePingRequest(ServerboundPingRequestPacket p_10093_) {
      this.connection.send(new ClientboundPongResponsePacket(p_10093_.getTime()));
      this.connection.disconnect(DISCONNECT_REASON);
   }
}
