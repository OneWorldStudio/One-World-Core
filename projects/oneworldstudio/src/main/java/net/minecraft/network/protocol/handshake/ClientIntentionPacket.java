package net.minecraft.network.protocol.handshake;

import com.google.gson.Gson;
import com.mojang.authlib.properties.Property;
import java.util.Objects;
import net.minecraft.SharedConstants;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkHooks;
import org.spigotmc.SpigotConfig;

public class ClientIntentionPacket implements Packet<ServerHandshakePacketListener> {
   private static final int MAX_HOST_LENGTH = 255;
   private final int protocolVersion;
   public final String hostName;
   public String original_ip;
   public final int port;
   private final ConnectionProtocol intention;
   private String fmlVersion = net.minecraftforge.network.NetworkConstants.NETVERSION;

   // Mohist start - Support ip forward
   private static final String EXTRA_DATA = "extraData";
   private static final Gson GSON = new Gson();
   // Mohist end

   public ClientIntentionPacket(String p_134726_, int p_134727_, ConnectionProtocol p_134728_) {
      this.protocolVersion = SharedConstants.getCurrentVersion().getProtocolVersion();
      this.hostName = p_134726_;
      this.port = p_134727_;
      this.intention = p_134728_;
   }

   public ClientIntentionPacket(FriendlyByteBuf p_179801_) {
      this.protocolVersion = p_179801_.readVarInt();
      String hostName = p_179801_.readUtf(Short.MAX_VALUE); // Spigot
      this.port = p_179801_.readUnsignedShort();
      this.intention = ConnectionProtocol.getById(p_179801_.readVarInt());
      this.fmlVersion = net.minecraftforge.network.NetworkHooks.getFMLVersion(hostName);
      // Mohist start - Support ip forward
      if (SpigotConfig.bungee && !Objects.equals(this.fmlVersion, net.minecraftforge.network.NetworkConstants.NETVERSION)) {
         String[] split = hostName.split("\0");
         if (split.length == 4) {
            Property[] properties = GSON.fromJson(split[3], Property[].class);
            for (Property property : properties) {
               if (Objects.equals(property.getName(), EXTRA_DATA)) {
                  String extraData = property.getValue().replace("\1", "\0");
                  this.fmlVersion = NetworkHooks.getFMLVersion(split[0] + extraData);
               }
            }
         }
      }
      this.original_ip = hostName;
      // Mohist end
      this.hostName = hostName.split("\0")[0];
   }

   public void write(FriendlyByteBuf p_134737_) {
      p_134737_.writeVarInt(this.protocolVersion);
      p_134737_.writeUtf(this.hostName + "\0"+ net.minecraftforge.network.NetworkConstants.NETVERSION+"\0");
      p_134737_.writeShort(this.port);
      p_134737_.writeVarInt(this.intention.getId());
   }

   public void handle(ServerHandshakePacketListener p_134734_) {
      p_134734_.handleIntention(this);
   }

   public ConnectionProtocol getIntention() {
      return this.intention;
   }

   public int getProtocolVersion() {
      return this.protocolVersion;
   }

   public String getHostName() {
      return this.hostName;
   }

   public int getPort() {
      return this.port;
   }

   public String getFMLVersion() {
      return this.fmlVersion;
   }
}
