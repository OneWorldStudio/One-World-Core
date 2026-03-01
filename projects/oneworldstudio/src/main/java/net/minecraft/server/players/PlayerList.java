package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.oneworldstudiomc.plugins.world.WorldManage;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.network.DualStackUtils;
import net.minecraftforge.network.NetworkHooks;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.command.ColouredConsoleSender;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftLocation;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.slf4j.Logger;
import org.spigotmc.AsyncCatcher;
import org.spigotmc.SpigotConfig;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public abstract class PlayerList {
   public static final File USERBANLIST_FILE = new File("banned-players.json");
   public static final File IPBANLIST_FILE = new File("banned-ips.json");
   public static final File OPLIST_FILE = new File("ops.json");
   public static final File WHITELIST_FILE = new File("whitelist.json");
   public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SEND_PLAYER_INFO_INTERVAL = 600;
   private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
   private final MinecraftServer server;
   public final List<ServerPlayer> players = new CopyOnWriteArrayList<>(); // CraftBukkit - ArrayList -> CopyOnWriteArrayList: Iterator safety
   private final Map<UUID, ServerPlayer> playersByUUID = Maps.newHashMap();
   private final UserBanList bans = new UserBanList(USERBANLIST_FILE);
   private final IpBanList ipBans = new IpBanList(IPBANLIST_FILE);
   private final ServerOpList ops = new ServerOpList(OPLIST_FILE);
   private final UserWhiteList whitelist = new UserWhiteList(WHITELIST_FILE);
   private final Map<UUID, ServerStatsCounter> stats = Maps.newHashMap();
   private final Map<UUID, PlayerAdvancements> advancements = Maps.newHashMap();
   public final PlayerDataStorage playerIo;
   private boolean doWhiteList;
   private final LayeredRegistryAccess<RegistryLayer> registries;
   private final RegistryAccess.Frozen synchronizedRegistries;
   public int maxPlayers;
   private int viewDistance;
   private int simulationDistance;
   private boolean allowCheatsForAllPlayers;
   private static final boolean ALLOW_LOGOUTIVATOR = false;
   private int sendAllPlayerInfoIn;
   private final List<ServerPlayer> playersView = Collections.unmodifiableList(players);

   // CraftBukkit start
   private CraftServer cserver;
   private final Map<String,ServerPlayer> playersByName = new HashMap<>();

   public PlayerList(MinecraftServer p_203842_, LayeredRegistryAccess<RegistryLayer> p_251844_, PlayerDataStorage p_203844_, int p_203845_) {
      this.cserver = p_203842_.server = new CraftServer((DedicatedServer) p_203842_, this);
      p_203842_.console = ColouredConsoleSender.getInstance();
      // CraftBukkit end
      this.server = p_203842_;
      this.registries = p_251844_;
      this.synchronizedRegistries = (new RegistryAccess.ImmutableRegistryAccess(RegistrySynchronization.networkedRegistries(p_251844_))).freeze();
      this.maxPlayers = p_203845_;
      this.playerIo = p_203844_;
   }

   public void placeNewPlayer(Connection p_11262_, ServerPlayer p_11263_) {
      GameProfile gameprofile = p_11263_.getGameProfile();
      GameProfileCache gameprofilecache = this.server.getProfileCache();
      String s;
      if (gameprofilecache != null) {
         Optional<GameProfile> optional = gameprofilecache.get(gameprofile.getId());
         s = optional.map(GameProfile::getName).orElse(gameprofile.getName());
         gameprofilecache.add(gameprofile);
      } else {
         s = gameprofile.getName();
      }

      CompoundTag compoundtag = this.load(p_11263_);
      // CraftBukkit start - Better rename detection
      if (compoundtag != null && compoundtag.contains("bukkit")) {
         CompoundTag bukkit = compoundtag.getCompound("bukkit");
         s = bukkit.contains("lastKnownName", 8) ? bukkit.getString("lastKnownName") : s;
      }
      // CraftBukkit end
      ResourceKey<Level> resourcekey = compoundtag != null ? DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, compoundtag.get("Dimension"))).resultOrPartial(LOGGER::error).orElse(Level.OVERWORLD) : Level.OVERWORLD;
      ServerLevel serverlevel = this.server.getLevel(resourcekey);
      ServerLevel serverlevel1;
      if (serverlevel == null) {
         LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", (Object)resourcekey);
         serverlevel1 = this.server.overworld();
      } else {
         serverlevel1 = serverlevel;
      }

      p_11263_.setServerLevel(serverlevel1);
      String s1 = "local";
      if (p_11262_.getRemoteAddress() != null) {
         s1 = DualStackUtils.getAddressString(p_11262_.getRemoteAddress());
      }

      // Spigot start - spawn location event
      org.bukkit.entity.Player spawnPlayer = p_11263_.getBukkitEntity();
      PlayerSpawnLocationEvent ev = new PlayerSpawnLocationEvent(spawnPlayer, spawnPlayer.getLocation());
      cserver.getPluginManager().callEvent(ev);

      Location loc = ev.getSpawnLocation();
      serverlevel1 = ((CraftWorld) loc.getWorld()).getHandle();

      p_11263_.spawnIn(serverlevel1);
      p_11263_.gameMode.setLevel((ServerLevel) p_11263_.level);
      p_11263_.absMoveTo(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
      // Spigot end

      LevelData leveldata = serverlevel1.getLevelData();
      p_11263_.loadGameTypes(compoundtag);
      ServerGamePacketListenerImpl servergamepacketlistenerimpl = new ServerGamePacketListenerImpl(this.server, p_11262_, p_11263_);
      NetworkHooks.sendMCRegistryPackets(p_11262_, "PLAY_TO_CLIENT");
      GameRules gamerules = serverlevel1.getGameRules();
      boolean flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
      boolean flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);
      // Spigot - view distance
      servergamepacketlistenerimpl.send(new ClientboundLoginPacket(p_11263_.getId(), leveldata.isHardcore(), p_11263_.gameMode.getGameModeForPlayer(), p_11263_.gameMode.getPreviousGameModeForPlayer(), this.server.levelKeys(), this.synchronizedRegistries, serverlevel1.dimensionTypeId(), serverlevel1.dimension(), BiomeManager.obfuscateSeed(serverlevel1.getSeed()), this.getMaxPlayers(), serverlevel1.spigotConfig.viewDistance, serverlevel1.spigotConfig.simulationDistance, flag1, !flag, serverlevel1.isDebug(), serverlevel1.isFlat(), p_11263_.getLastDeathLocation(), p_11263_.getPortalCooldown()));
      p_11263_.getBukkitEntity().sendSupportedChannels(); // CraftBukkit
      servergamepacketlistenerimpl.send(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(serverlevel1.enabledFeatures())));
      servergamepacketlistenerimpl.send(new ClientboundCustomPayloadPacket(ClientboundCustomPayloadPacket.BRAND, (new FriendlyByteBuf(Unpooled.buffer())).writeUtf(this.getServer().getServerModName())));
      servergamepacketlistenerimpl.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
      servergamepacketlistenerimpl.send(new ClientboundPlayerAbilitiesPacket(p_11263_.getAbilities()));
      servergamepacketlistenerimpl.send(new ClientboundSetCarriedItemPacket(p_11263_.getInventory().selected));
      MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(this, p_11263_));
      servergamepacketlistenerimpl.send(new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getRecipes()));
      servergamepacketlistenerimpl.send(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
      this.sendPlayerPermissionLevel(p_11263_);
      p_11263_.getStats().markAllDirty();
      p_11263_.getRecipeBook().sendInitialRecipeBook(p_11263_);
      this.updateEntireScoreboard(serverlevel1.getScoreboard(), p_11263_);
      this.server.invalidateStatus();
      MutableComponent mutablecomponent;
      if (p_11263_.getGameProfile().getName().equalsIgnoreCase(s)) {
         mutablecomponent = Component.translatable("multiplayer.player.joined", p_11263_.getDisplayName());
      } else {
         mutablecomponent = Component.translatable("multiplayer.player.joined.renamed", p_11263_.getDisplayName(), s);
      }

      // CraftBukkit start
      mutablecomponent.withStyle(ChatFormatting.YELLOW);
      String joinMessage = CraftChatMessage.fromComponent(mutablecomponent);

      servergamepacketlistenerimpl.teleport(p_11263_.getX(), p_11263_.getY(), p_11263_.getZ(), p_11263_.getYRot(), p_11263_.getXRot());
      ServerStatus serverstatus = this.server.getStatus();
      if (serverstatus != null) {
         p_11263_.sendServerStatus(serverstatus);
      }

      // pPlayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players)); // CraftBukkit - replaced with loop below
      this.players.add(p_11263_);
      this.playersByName.put(p_11263_.getScoreboardName().toLowerCase(Locale.ROOT), p_11263_); // Spigot
      this.playersByUUID.put(p_11263_.getUUID(), p_11263_);

      // CraftBukkit start
      CraftPlayer bukkitPlayer = p_11263_.getBukkitEntity();

      // Ensure that player inventory is populated with its viewer
      p_11263_.containerMenu.transferTo(p_11263_.containerMenu, bukkitPlayer);

      PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(bukkitPlayer, joinMessage);
      cserver.getPluginManager().callEvent(playerJoinEvent);

      if (!p_11263_.connection.isAcceptingMessages()) {
         return;
      }

      joinMessage = playerJoinEvent.getJoinMessage();

      if (joinMessage != null && !joinMessage.isEmpty()) {
         for (Component line : CraftChatMessage.fromString(joinMessage)) {
            server.getPlayerList().broadcastSystemMessage(line, false);
         }
      }
      // CraftBukkit end

      // CraftBukkit start - sendAll above replaced with this loop
      ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(p_11263_));

      for (ServerPlayer player : this.players) {
         if (player.getBukkitEntity().canSee(bukkitPlayer)) {
            player.connection.send(packet);
         }

         if (!bukkitPlayer.canSee(player.getBukkitEntity())) {
            continue;
         }

         p_11263_.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
      }
      p_11263_.sentListPacket = true;
      // CraftBukkit end

      p_11263_.getEntityData().refresh(p_11263_); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn

      this.sendLevelInfo(p_11263_, serverlevel1);

      // CraftBukkit start - Only add if the player wasn't moved in the event
      if (p_11263_.level == serverlevel1 && !serverlevel1.players().contains(p_11263_)) {
         serverlevel1.addNewPlayer(p_11263_);
         this.server.getCustomBossEvents().onPlayerConnect(p_11263_);
      }
      serverlevel1 = p_11263_.serverLevel(); // CraftBukkit - Update in case join event changed it
      // CraftBukkit end
      this.server.getServerResourcePack().ifPresent((p_215606_) -> {
         p_11263_.sendTexturePack(p_215606_.url(), p_215606_.hash(), p_215606_.isRequired(), p_215606_.prompt());
      });

      for(MobEffectInstance mobeffectinstance : p_11263_.getActiveEffects()) {
         servergamepacketlistenerimpl.send(new ClientboundUpdateMobEffectPacket(p_11263_.getId(), mobeffectinstance));
      }

      if (compoundtag != null && compoundtag.contains("RootVehicle", 10)) {
         CompoundTag compoundtag1 = compoundtag.getCompound("RootVehicle");
         ServerLevel finalServerlevel = serverlevel1;
         Entity entity1 = EntityType.loadEntityRecursive(compoundtag1.getCompound("Entity"), finalServerlevel, (p_215603_) -> {
            return !finalServerlevel.addWithUUID(p_215603_) ? null : p_215603_;
         });
         if (entity1 != null) {
            UUID uuid;
            if (compoundtag1.hasUUID("Attach")) {
               uuid = compoundtag1.getUUID("Attach");
            } else {
               uuid = null;
            }

            if (entity1.getUUID().equals(uuid)) {
               p_11263_.startRiding(entity1, true);
            } else {
               for(Entity entity : entity1.getIndirectPassengers()) {
                  if (entity.getUUID().equals(uuid)) {
                     p_11263_.startRiding(entity, true);
                     break;
                  }
               }
            }

            if (!p_11263_.isPassenger()) {
               LOGGER.warn("Couldn't reattach entity to player");
               entity1.discard();

               for(Entity entity2 : entity1.getIndirectPassengers()) {
                  entity2.discard();
               }
            }
         }
      }

      p_11263_.initInventoryMenu();
      ForgeEventFactory.firePlayerLoggedIn( p_11263_ );
      LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", p_11263_.getName().getString(), s1, p_11263_.getId(), serverlevel1.K.getLevelName(), p_11263_.getX(), p_11263_.getY(), p_11263_.getZ());
   }

   public void updateEntireScoreboard(ServerScoreboard p_11274_, ServerPlayer p_11275_) {
      Set<Objective> set = Sets.newHashSet();

      for(PlayerTeam playerteam : p_11274_.getPlayerTeams()) {
         p_11275_.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerteam, true));
      }

      for(int i = 0; i < 19; ++i) {
         Objective objective = p_11274_.getDisplayObjective(i);
         if (objective != null && !set.contains(objective)) {
            for(Packet<?> packet : p_11274_.getStartTrackingPackets(objective)) {
               p_11275_.connection.send(packet);
            }

            set.add(objective);
         }
      }

   }

   public void addWorldborderListener(ServerLevel p_184210_) {
      if (playerIo != null) return; // CraftBukkit
      p_184210_.getWorldBorder().addListener(new BorderChangeListener() {
         public void onBorderSizeSet(WorldBorder p_11321_, double p_11322_) {
            PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(p_11321_));
         }

         public void onBorderSizeLerping(WorldBorder p_11328_, double p_11329_, double p_11330_, long p_11331_) {
            PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(p_11328_));
         }

         public void onBorderCenterSet(WorldBorder p_11324_, double p_11325_, double p_11326_) {
            PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(p_11324_));
         }

         public void onBorderSetWarningTime(WorldBorder p_11333_, int p_11334_) {
            PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(p_11333_));
         }

         public void onBorderSetWarningBlocks(WorldBorder p_11339_, int p_11340_) {
            PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(p_11339_));
         }

         public void onBorderSetDamagePerBlock(WorldBorder p_11336_, double p_11337_) {
         }

         public void onBorderSetDamageSafeZOne(WorldBorder p_11342_, double p_11343_) {
         }
      });
   }

   @Nullable
   public CompoundTag load(ServerPlayer p_11225_) {
      CompoundTag compoundtag = this.server.getWorldData().getLoadedPlayerTag();
      CompoundTag compoundtag1;
      if (this.server.isSingleplayerOwner(p_11225_.getGameProfile()) && compoundtag != null) {
         compoundtag1 = compoundtag;
         p_11225_.load(compoundtag);
         LOGGER.debug("loading single player");
         ForgeEventFactory.firePlayerLoadingEvent(p_11225_, this.playerIo, p_11225_.getUUID().toString());
      } else {
         compoundtag1 = this.playerIo.load(p_11225_);
      }

      return compoundtag1;
   }

   protected void save(ServerPlayer p_11277_) {
      if (!p_11277_.getBukkitEntity().isPersistent()) return; // CraftBukkit
      this.playerIo.save(p_11277_);
      ServerStatsCounter serverstatscounter = this.stats.get(p_11277_.getUUID());
      if (serverstatscounter != null) {
         serverstatscounter.save();
      }

      PlayerAdvancements playeradvancements = this.advancements.get(p_11277_.getUUID());
      if (playeradvancements != null) {
         playeradvancements.save();
      }

   }

   public String quitMessage;
   public void remove(ServerPlayer p_11287_) {
      ForgeEventFactory.firePlayerLoggedOut(p_11287_);
      ServerLevel serverlevel = p_11287_.serverLevel();
      p_11287_.awardStat(Stats.LEAVE_GAME);

      // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
      // See SPIGOT-5799, SPIGOT-6145
      if (p_11287_.containerMenu != p_11287_.inventoryMenu) {
         p_11287_.closeContainer();
      }

      PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(p_11287_.getBukkitEntity(), p_11287_.kickLeaveMessage != null ? p_11287_.kickLeaveMessage : "\u00A7e" + p_11287_.getScoreboardName() + " left the game");
      cserver.getPluginManager().callEvent(playerQuitEvent);
      p_11287_.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());

      p_11287_.doTick(); // SPIGOT-924
      // CraftBukkit end

      this.save(p_11287_);
      if (p_11287_.isPassenger()) {
         Entity entity = p_11287_.getRootVehicle();
         if (entity.hasExactlyOnePlayerPassenger()) {
            LOGGER.debug("Removing player mount");
            p_11287_.stopRiding();
            entity.getPassengersAndSelf().forEach((p_215620_) -> {
               p_215620_.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            });
         }
      }

      p_11287_.unRide();
      serverlevel.removePlayerImmediately(p_11287_, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
      p_11287_.getAdvancements().stopListening();
      this.players.remove(p_11287_);
      this.playersByName.remove(p_11287_.getScoreboardName().toLowerCase(Locale.ROOT)); // Spigot
      this.server.getCustomBossEvents().onPlayerDisconnect(p_11287_);
      UUID uuid = p_11287_.getUUID();
      ServerPlayer serverplayer = this.playersByUUID.get(uuid);
      if (serverplayer == p_11287_) {
         this.playersByUUID.remove(uuid);
         this.stats.remove(uuid);
         this.advancements.remove(uuid);
      }

      // CraftBukkit start
      ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(p_11287_.getUUID()));
      for (ServerPlayer player : players) {

         if (player.getBukkitEntity().canSee(p_11287_.getBukkitEntity())) {
            player.connection.send(packet);
         } else {
            player.getBukkitEntity().onEntityRemove(p_11287_);
         }
      }
      // This removes the scoreboard (and player reference) for the specific player in the manager
      cserver.getScoreboardManager().removePlayer(p_11287_.getBukkitEntity());
      // CraftBukkit end
      quitMessage = playerQuitEvent.getQuitMessage();
   }

   // Mohist start
   private final AtomicReference<ServerPlayer> entity = new AtomicReference<>(null);
   private final AtomicReference<ServerLoginPacketListenerImpl> handler = new AtomicReference<>(null);

   public void mohist$putHandler(ServerLoginPacketListenerImpl handler) {
      this.handler.set(handler);
   }
   @Nullable
   public Component canPlayerLogin(SocketAddress p_11257_, GameProfile p_11258_) {
      ServerPlayer serverPlayer = getPlayerForLogin(p_11258_);
      entity.set(serverPlayer);
      org.bukkit.entity.Player player = serverPlayer.getBukkitEntity();
      ServerLoginPacketListenerImpl handleR = handler.getAndSet(null);
      String hostname = handleR == null ? "" : handleR.connection.hostname;
      InetAddress realAddress = handleR == null ? ((InetSocketAddress) p_11257_).getAddress() : ((InetSocketAddress) handleR.connection.channel.remoteAddress()).getAddress();
      PlayerLoginEvent event = null;
      if (!AsyncCatcher.catchAsync()) {
         event = new PlayerLoginEvent(player, hostname, ((InetSocketAddress) p_11257_).getAddress(), realAddress);
      }
      if (getBans().isBanned(p_11258_) && !getBans().get(p_11258_).hasExpired()) {
         UserBanListEntry userbanlistentry = this.bans.get(p_11258_);
         MutableComponent mutablecomponent1 = Component.translatable("multiplayer.disconnect.banned.reason", userbanlistentry.getReason());
         if (userbanlistentry.getExpires() != null) {
            mutablecomponent1.append(Component.translatable("multiplayer.disconnect.banned.expiration", BAN_DATE_FORMAT.format(userbanlistentry.getExpires())));
         }
         if (event != null) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, SpigotConfig.whitelistMessage); // Spigot
         } else {
            return mutablecomponent1;
         }
      } else if (!this.isWhiteListed(p_11258_)) {
         MutableComponent mutablecomponent1 = Component.translatable("multiplayer.disconnect.not_whitelisted");
         if (event != null) {
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, CraftChatMessage.fromComponent(mutablecomponent1));
         } else {
            return mutablecomponent1;
         }
      } else if (getIpBans().isBanned(p_11257_) && !getIpBans().get(p_11257_).hasExpired()) {
         IpBanListEntry ipbanlistentry = this.ipBans.get(p_11257_);
         MutableComponent mutablecomponent1 = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipbanlistentry.getReason());
         if (ipbanlistentry.getExpires() != null) {
            mutablecomponent1.append(Component.translatable("multiplayer.disconnect.banned_ip.expiration", BAN_DATE_FORMAT.format(ipbanlistentry.getExpires())));
         }
         if (event != null) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, CraftChatMessage.fromComponent(mutablecomponent1));
         } else {
            return mutablecomponent1;
         }
      } else {
         if (this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(p_11258_)) {
            if (event != null) {
               event.disallow(PlayerLoginEvent.Result.KICK_FULL, SpigotConfig.serverFullMessage); // Spigot
            } else {
               return Component.literal(SpigotConfig.serverFullMessage);
            }
         }
      }

      if (event != null) {
         cserver.getPluginManager().callEvent(event);
         if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            entity.set(null);
            return Component.literal(event.getKickMessage());
         }
      }
      return null;
   }

   public ServerPlayer getPlayerForLogin(GameProfile p_215625_) {
      if(entity.get() != null) {
         return entity.getAndSet(null);
      }
      UUID uuid = UUIDUtil.getOrCreatePlayerUUID(p_215625_);
      List<ServerPlayer> list = Lists.newArrayList();

      for(int i = 0; i < this.players.size(); ++i) {
         ServerPlayer serverplayer = this.players.get(i);
         if (serverplayer.getUUID().equals(uuid)) {
            list.add(serverplayer);
         }
      }

      ServerPlayer serverplayer2 = this.playersByUUID.get(p_215625_.getId());
      if (serverplayer2 != null && !list.contains(serverplayer2)) {
         list.add(serverplayer2);
      }

      for(ServerPlayer serverplayer1 : list) {
         serverplayer1.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
      }

      return new ServerPlayer(this.server, this.server.overworld(), p_215625_);
   }

   // Mohist TODO start
   public Location mohist$location = null;
   public PlayerRespawnEvent.RespawnReason mohist$reason = null;
   public ServerLevel mohist$worldserver = null;
   public AtomicBoolean avoidSuffocation = new AtomicBoolean(true);
   public World fromWorld = null;

   public ServerPlayer respawn(ServerPlayer p_11237_, boolean p_11238_) {
      p_11237_.stopRiding(); // CraftBukkit
      this.players.remove(p_11237_);
      this.playersByName.remove(p_11237_.getScoreboardName().toLowerCase(Locale.ROOT)); // Spigot
      p_11237_.serverLevel().removePlayerImmediately(p_11237_, Entity.RemovalReason.DISCARDED);
      BlockPos blockpos = p_11237_.getRespawnPosition();
      float f = p_11237_.getRespawnAngle();
      boolean flag = p_11237_.isRespawnForced();

      fromWorld = p_11237_.getBukkitEntity().getWorld();
      p_11237_.wonGame = false;

      boolean flag2 = false;
      ServerLevel worldserver1 = this.server.getLevel(p_11237_.getRespawnDimension());
      if (mohist$location == null) {
         boolean isBedSpawn = false;
         if (worldserver1 != null) {
            Optional<Vec3> optional;
            if (blockpos != null) {
               optional = Player.findRespawnPositionAndUseSpawnBlock(worldserver1, blockpos, f, flag, p_11238_);
            } else {
               optional = Optional.empty();
            }
            if (optional.isPresent()) {
               BlockState iblockdata = worldserver1.getBlockState(blockpos);
               boolean flag3 = iblockdata.is(Blocks.RESPAWN_ANCHOR);
               Vec3 vec3d = optional.get();
               float f1;
               if (!iblockdata.is(BlockTags.BEDS) && !flag3) {
                  f1 = f;
               } else {
                  Vec3 vec3d2 = Vec3.atBottomCenterOf(blockpos).subtract(vec3d).normalize();
                  f1 = (float) Mth.wrapDegrees(Mth.atan2(vec3d2.z, vec3d2.x) * 57.2957763671875 - 90.0);
               }

               flag2 = (!p_11238_ && flag3);
               isBedSpawn = true;
               mohist$location = CraftLocation.toBukkit(vec3d, worldserver1.getWorld(), f1, 0.0F);
            } else if (blockpos != null) {
               p_11237_.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0f));
               p_11237_.setRespawnPosition(null, null, 0f, false, false, PlayerSpawnChangeEvent.Cause.RESET);
            }
         }
         if (mohist$location == null) {
            worldserver1 = this.server.getLevel(Level.OVERWORLD);
            blockpos = p_11237_.getSpawnPoint(worldserver1);
            mohist$location = new Location(worldserver1.getWorld(), blockpos.getX() + 0.5f, blockpos.getY() + 0.1f, blockpos.getZ() + 0.5f);
         }
         org.bukkit.entity.Player respawnPlayer = p_11237_.getBukkitEntity();
         PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(respawnPlayer, mohist$location, isBedSpawn && !flag2, flag2, mohist$reason);
         this.cserver.getPluginManager().callEvent(respawnEvent);
         if (p_11237_.connection.isDisconnected()) {
            return p_11237_;
         }
         mohist$location = respawnEvent.getRespawnLocation();
         if (!p_11238_) {
            p_11237_.reset();
         }
      } else {
         if (mohist$worldserver == null) mohist$worldserver = this.server.getLevel(p_11237_.getRespawnDimension());
         mohist$location.setWorld(mohist$worldserver.getWorld());
      }
      // CraftBukkit end

      ServerLevel serverlevel1 = ((CraftWorld) mohist$location.getWorld()).getHandle();
      ServerPlayer serverplayer = new ServerPlayer(this.server, serverlevel1, p_11237_.getGameProfile());
      serverplayer.getBukkitEntity().restore(p_11237_.getBukkitEntity());
      serverplayer.setBukkitEntity(p_11237_.getBukkitEntity());
      p_11237_.connection.player = serverplayer;
      serverplayer.connection = p_11237_.connection;

      serverplayer.restoreFrom(p_11237_, p_11238_);
      serverplayer.reviveCaps();
      serverplayer.setRespawnPosition(p_11237_.getRespawnDimension(), p_11237_.getRespawnPosition(), p_11237_.getRespawnAngle(), p_11237_.isRespawnForced(), false);
      serverplayer.setId(p_11237_.getId());
      serverplayer.setMainArm(p_11237_.getMainArm());

      for (String s : p_11237_.getTags()) {
         serverplayer.addTag(s);
      }

      serverplayer.forceSetPositionRotation(mohist$location.getX(), mohist$location.getY(), mohist$location.getZ(), mohist$location.getYaw(), mohist$location.getPitch());
      // CraftBukkit end

      while (avoidSuffocation.getAndSet(true) && !serverlevel1.noCollision(serverplayer) && serverplayer.getY() < (double) serverlevel1.getMaxBuildHeight()) {
         serverplayer.setPos(serverplayer.getX(), serverplayer.getY() + 1.0D, serverplayer.getZ());
      }

      byte b0 = (byte)(p_11238_ ? 1 : 0);
      // CraftBukkit start
      LevelData worlddata = serverlevel1.getLevelData();
      serverplayer.connection.send(new ClientboundRespawnPacket(serverlevel1.dimensionTypeId(), serverlevel1.dimension(), BiomeManager.obfuscateSeed(serverlevel1.getSeed()), serverplayer.gameMode.getGameModeForPlayer(), serverplayer.gameMode.getPreviousGameModeForPlayer(), serverlevel1.isDebug(), serverlevel1.isFlat(), b0, serverplayer.getLastDeathLocation(), serverplayer.getPortalCooldown()));
      serverplayer.connection.send(new ClientboundSetChunkCacheRadiusPacket(serverlevel1.spigotConfig.viewDistance)); // Spigot
      serverplayer.connection.send(new ClientboundSetSimulationDistancePacket(serverlevel1.spigotConfig.simulationDistance)); // Spigot
      serverplayer.spawnIn(serverlevel1);
      serverplayer.unsetRemoved();
      serverplayer.connection.teleport(CraftLocation.toBukkit(serverplayer.position(), serverlevel1.getWorld(), serverplayer.getYRot(), serverplayer.getXRot()));
      serverplayer.setShiftKeyDown(false);
      serverplayer.connection.send(new ClientboundSetDefaultSpawnPositionPacket(serverlevel1.getSharedSpawnPos(), serverlevel1.getSharedSpawnAngle()));
      serverplayer.connection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
      serverplayer.connection.send(new ClientboundSetExperiencePacket(serverplayer.experienceProgress, serverplayer.totalExperience, serverplayer.experienceLevel));
      this.sendLevelInfo(serverplayer, serverlevel1);
      this.sendPlayerPermissionLevel(serverplayer);
      if (!p_11237_.connection.isDisconnected()) {
         serverlevel1.addRespawnedPlayer(serverplayer);
         this.players.add(serverplayer);
         this.playersByName.put(serverplayer.getScoreboardName().toLowerCase(Locale.ROOT), serverplayer); // Spigot
         this.playersByUUID.put(serverplayer.getUUID(), serverplayer);
      }
      serverplayer.initInventoryMenu();
      serverplayer.setHealth(serverplayer.getHealth());
      ForgeEventFactory.firePlayerRespawnEvent(serverplayer, p_11238_);
      if (flag2) {
         serverplayer.connection.send(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, mohist$location.getX(), mohist$location.getY(), mohist$location.getZ(), 1.0F, 1.0F, serverlevel1.getRandom().nextLong()));
      }
      // Added from changeDimension
      this.sendAllPlayerInfo(serverplayer); // Update health, etc...
      serverplayer.onUpdateAbilities();
      for (MobEffectInstance mobEffect : serverplayer.getActiveEffects()) {
         serverplayer.connection.send(new ClientboundUpdateMobEffectPacket(serverplayer.getId(), mobEffect));
      }

      // Fire advancement trigger
      serverplayer.triggerDimensionChangeTriggers(((CraftWorld) fromWorld).getHandle());

      // Don't fire on respawn
      if (fromWorld != mohist$location.getWorld()) {
         WorldManage.changeGameMode(serverplayer, mohist$location.getWorld()); // Mohist
         PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(serverplayer.getBukkitEntity(), fromWorld);
         server.server.getPluginManager().callEvent(event);
      }

      // Save player file again if they were disconnected
      if (serverplayer.connection.isDisconnected()) {
         this.save(serverplayer);
      }

      // CraftBukkit end
      mohist$location = null; // Mohist
      mohist$worldserver = null; // Mohist
      this.mohist$reason = null; // Mohist
      return serverplayer;
   }

   public ServerPlayer respawn(ServerPlayer entityplayer, boolean flag, PlayerRespawnEvent.RespawnReason reason) {
      return this.respawn(entityplayer, this.server.getLevel(entityplayer.getRespawnDimension()), flag, null, true, reason);
   }

   public ServerPlayer respawn(ServerPlayer entityplayer, ServerLevel worldserver, boolean flag, Location location, boolean avoidSuffocation, PlayerRespawnEvent.RespawnReason reason) {
      this.mohist$location = location;
      this.mohist$worldserver = worldserver;
      this.mohist$reason = reason;
      this.avoidSuffocation.set(avoidSuffocation);
      return respawn(entityplayer, flag);
   }
   // Mohist TODO end

   public void sendPlayerPermissionLevel(ServerPlayer p_11290_) {
      GameProfile gameprofile = p_11290_.getGameProfile();
      int i = this.server.getProfilePermissions(gameprofile);
      this.sendPlayerPermissionLevel(p_11290_, i);
   }

   public void tick() {
      if (++this.sendAllPlayerInfoIn > 600) {
         // CraftBukkit start
         for (int i = 0; i < this.players.size(); ++i) {
             final ServerPlayer target = (ServerPlayer) this.players.get(i);

             target.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), this.players.stream().filter(new Predicate<ServerPlayer>() {
                 @Override
                 public boolean test(ServerPlayer input) {
                     return target.getBukkitEntity().canSee(input.getBukkitEntity());
                 }
             }).collect(Collectors.toList())));
         }
         // CraftBukkit end
         this.sendAllPlayerInfoIn = 0;
      }

   }

   public void broadcastAll(Packet<?> p_11269_) {
      for(ServerPlayer serverplayer : this.players) {
         serverplayer.connection.send(p_11269_);
      }

   }

   // CraftBukkit start - add a world/entity limited version
   public void broadcastAll(Packet<?>  packet, Player entityhuman) {
      for (int i = 0; i < this.players.size(); ++i) {
         ServerPlayer entityplayer =  this.players.get(i);
         if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
            continue;
         }
         ((ServerPlayer) this.players.get(i)).connection.send(packet);
      }
   }

   public void broadcastAll(Packet<?>  packet, Level world) {
      for (int i = 0; i < world.players().size(); ++i) {
         ((ServerPlayer) world.players().get(i)).connection.send(packet);
      }

   }
   // CraftBukkit end

   public void broadcastAll(Packet<?> p_11271_, ResourceKey<Level> p_11272_) {
      for(ServerPlayer serverplayer : this.players) {
         if (serverplayer.level().dimension() == p_11272_) {
            serverplayer.connection.send(p_11271_);
         }
      }

   }

   public void broadcastSystemToTeam(Player p_215622_, Component p_215623_) {
      Team team = p_215622_.getTeam();
      if (team != null) {
         for(String s : team.getPlayers()) {
            ServerPlayer serverplayer = this.getPlayerByName(s);
            if (serverplayer != null && serverplayer != p_215622_) {
               serverplayer.sendSystemMessage(p_215623_);
            }
         }

      }
   }

   public void broadcastSystemToAllExceptTeam(Player p_215650_, Component p_215651_) {
      Team team = p_215650_.getTeam();
      if (team == null) {
         this.broadcastSystemMessage(p_215651_, false);
      } else {
         for(int i = 0; i < this.players.size(); ++i) {
            ServerPlayer serverplayer = this.players.get(i);
            if (serverplayer.getTeam() != team) {
               serverplayer.sendSystemMessage(p_215651_);
            }
         }

      }
   }

   public String[] getPlayerNamesArray() {
      String[] astring = new String[this.players.size()];

      for(int i = 0; i < this.players.size(); ++i) {
         astring[i] = this.players.get(i).getGameProfile().getName();
      }

      return astring;
   }

   public UserBanList getBans() {
      return this.bans;
   }

   public IpBanList getIpBans() {
      return this.ipBans;
   }

   public void op(GameProfile p_11254_) {
      if (ForgeEventFactory.onPermissionChanged(p_11254_, this.server.getOperatorUserPermissionLevel(), this)) return;
      this.ops.add(new ServerOpListEntry(p_11254_, this.server.getOperatorUserPermissionLevel(), this.ops.canBypassPlayerLimit(p_11254_)));
      ServerPlayer serverplayer = this.getPlayer(p_11254_.getId());
      if (serverplayer != null) {
         this.sendPlayerPermissionLevel(serverplayer);
      }

   }

   public void deop(GameProfile p_11281_) {
      if (ForgeEventFactory.onPermissionChanged(p_11281_, 0, this)) return;
      this.ops.remove(p_11281_);
      ServerPlayer serverplayer = this.getPlayer(p_11281_.getId());
      if (serverplayer != null) {
         this.sendPlayerPermissionLevel(serverplayer);
      }

   }

   private void sendPlayerPermissionLevel(ServerPlayer p_11227_, int p_11228_) {
      if (p_11227_.connection != null) {
         byte b0;
         if (p_11228_ <= 0) {
            b0 = 24;
         } else if (p_11228_ >= 4) {
            b0 = 28;
         } else {
            b0 = (byte)(24 + p_11228_);
         }

         p_11227_.connection.send(new ClientboundEntityEventPacket(p_11227_, b0));
      }

      p_11227_.getBukkitEntity().recalculatePermissions(); // CraftBukkit
      this.server.getCommands().sendCommands(p_11227_);
   }

   public boolean isWhiteListed(GameProfile p_11294_) {
      return !this.doWhiteList || this.ops.contains(p_11294_) || this.whitelist.contains(p_11294_);
   }

   public boolean isOp(GameProfile p_11304_) {
      return this.ops.contains(p_11304_) || this.server.isSingleplayerOwner(p_11304_) && this.server.getWorldData().getAllowCommands() || this.allowCheatsForAllPlayers;
   }

   @Nullable
   public ServerPlayer getPlayerByName(String p_11256_) {
      for(ServerPlayer serverplayer : this.players) {
         if (serverplayer.getGameProfile().getName().equalsIgnoreCase(p_11256_)) {
            return serverplayer;
         }
      }

      return null;
   }

   public void broadcast(@Nullable Player p_11242_, double p_11243_, double p_11244_, double p_11245_, double p_11246_, ResourceKey<Level> p_11247_, Packet<?> p_11248_) {
      for(int i = 0; i < this.players.size(); ++i) {
         ServerPlayer serverplayer = this.players.get(i);

         // CraftBukkit start - Test if player receiving packet can see the source of the packet
          if (p_11242_ != null && !serverplayer.getBukkitEntity().canSee(p_11242_.getBukkitEntity())) {
              continue;
          }
          // CraftBukkit end

         if (serverplayer != p_11242_ && serverplayer.level().dimension() == p_11247_) {
            double d0 = p_11243_ - serverplayer.getX();
            double d1 = p_11244_ - serverplayer.getY();
            double d2 = p_11245_ - serverplayer.getZ();
            if (d0 * d0 + d1 * d1 + d2 * d2 < p_11246_ * p_11246_) {
               serverplayer.connection.send(p_11248_);
            }
         }
      }

   }

   public void saveAll() {
      for(int i = 0; i < this.players.size(); ++i) {
         this.save(this.players.get(i));
      }

   }

   public UserWhiteList getWhiteList() {
      return this.whitelist;
   }

   public String[] getWhiteListNames() {
      return this.whitelist.getUserList();
   }

   public ServerOpList getOps() {
      return this.ops;
   }

   public String[] getOpNames() {
      return this.ops.getUserList();
   }

   public void reloadWhiteList() {
   }

   public void sendLevelInfo(ServerPlayer p_11230_, ServerLevel p_11231_) {
      WorldBorder worldborder = p_11230_.level.getWorldBorder();
      p_11230_.connection.send(new ClientboundInitializeBorderPacket(worldborder));
      p_11230_.connection.send(new ClientboundSetTimePacket(p_11231_.getGameTime(), p_11231_.getDayTime(), p_11231_.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
      p_11230_.connection.send(new ClientboundSetDefaultSpawnPositionPacket(p_11231_.getSharedSpawnPos(), p_11231_.getSharedSpawnAngle()));
      if (p_11231_.isRaining()) {
          // CraftBukkit start - handle player weather
          p_11230_.setPlayerWeather(WeatherType.DOWNFALL, false);
          p_11230_.updateWeather(-p_11231_.rainLevel, p_11231_.rainLevel, -p_11231_.thunderLevel, p_11231_.thunderLevel);
          // CraftBukkit end
      }

   }

   public void sendAllPlayerInfo(ServerPlayer p_11293_) {
      p_11293_.inventoryMenu.sendAllDataToRemote();
      // pPlayer.resetSentInfo();
      p_11293_.getBukkitEntity().updateScaledHealth(); // CraftBukkit - Update scaled health on respawn and worldchange
      p_11293_.getEntityData().refresh(p_11293_); // CraftBukkkit - SPIGOT-7218: sync metadata
      p_11293_.connection.send(new ClientboundSetCarriedItemPacket(p_11293_.getInventory().selected));
      // CraftBukkit start - from GameRules
      int i = p_11293_.level.getGameRules().getBoolean(GameRules.RULE_REDUCEDDEBUGINFO) ? 22 : 23;
      p_11293_.connection.send(new ClientboundEntityEventPacket(p_11293_, (byte) i));
      float immediateRespawn = p_11293_.level.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN) ? 1.0F : 0.0F;
      p_11293_.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, immediateRespawn));
      // CraftBukkit end
   }

   public int getPlayerCount() {
      return this.players.size();
   }

   public int getMaxPlayers() {
      return this.maxPlayers;
   }

   public boolean isUsingWhitelist() {
      return this.doWhiteList;
   }

   public void setUsingWhiteList(boolean p_11276_) {
      this.doWhiteList = p_11276_;
   }

   public List<ServerPlayer> getPlayersWithAddress(String p_11283_) {
      List<ServerPlayer> list = Lists.newArrayList();

      for(ServerPlayer serverplayer : this.players) {
         if (serverplayer.getIpAddress().equals(p_11283_)) {
            list.add(serverplayer);
         }
      }

      return list;
   }

   public int getViewDistance() {
      return this.viewDistance;
   }

   public int getSimulationDistance() {
      return this.simulationDistance;
   }

   public MinecraftServer getServer() {
      return this.server;
   }

   @Nullable
   public CompoundTag getSingleplayerData() {
      return null;
   }

   public void setAllowCheatsForAllPlayers(boolean p_11285_) {
      this.allowCheatsForAllPlayers = p_11285_;
   }

   public void removeAll() {
      // CraftBukkit start - disconnect safely
      for (ServerPlayer player : this.players) {
         player.connection.disconnect(this.server.server.getShutdownMessage()); // CraftBukkit - add custom shutdown message
      }
      // CraftBukkit end

   }

   // CraftBukkit start
   public void broadcastMessage(Component[] iChatBaseComponents) {
      for (Component component : iChatBaseComponents) {
         broadcastSystemMessage(component, false);
      }
   }
   // CraftBukkit end

   public void broadcastSystemMessage(Component p_240618_, boolean p_240644_) {
      this.broadcastSystemMessage(p_240618_, (p_215639_) -> {
         return p_240618_;
      }, p_240644_);
   }

   public void broadcastSystemMessage(Component p_240526_, Function<ServerPlayer, Component> p_240594_, boolean p_240648_) {
      this.server.sendSystemMessage(p_240526_);

      for(ServerPlayer serverplayer : this.players) {
         Component component = p_240594_.apply(serverplayer);
         if (component != null) {
            serverplayer.sendSystemMessage(component, p_240648_);
         }
      }

   }

   public void broadcastChatMessage(PlayerChatMessage p_243229_, CommandSourceStack p_243254_, ChatType.Bound p_243255_) {
      this.broadcastChatMessage(p_243229_, p_243254_::shouldFilterMessageTo, p_243254_.getPlayer(), p_243255_);
   }

   public void broadcastChatMessage(PlayerChatMessage p_243264_, ServerPlayer p_243234_, ChatType.Bound p_243204_) {
      this.broadcastChatMessage(p_243264_, p_243234_::shouldFilterMessageTo, p_243234_, p_243204_);
   }

   private void broadcastChatMessage(PlayerChatMessage p_249952_, Predicate<ServerPlayer> p_250784_, @Nullable ServerPlayer p_249623_, ChatType.Bound p_250276_) {
      boolean flag = this.verifyChatTrusted(p_249952_);
      this.server.logChatMessage(p_249952_.decoratedContent(), p_250276_, flag ? null : "Not Secure");
      OutgoingChatMessage outgoingchatmessage = OutgoingChatMessage.create(p_249952_);
      boolean flag1 = false;

      for(ServerPlayer serverplayer : this.players) {
         boolean flag2 = p_250784_.test(serverplayer);
         serverplayer.sendChatMessage(outgoingchatmessage, flag2, p_250276_);
         flag1 |= flag2 && p_249952_.isFullyFiltered();
      }

      if (flag1 && p_249623_ != null) {
         p_249623_.sendSystemMessage(CHAT_FILTERED_FULL);
      }

   }

   private boolean verifyChatTrusted(PlayerChatMessage p_251384_) {
      return true; // Mohist chat verify
   }

   public ServerStatsCounter getPlayerStats(Player p_11240_) { // Mohist TODO
      UUID uuid = p_11240_.getUUID();
      ServerStatsCounter serverstatscounter = this.stats.get(uuid);
      if (serverstatscounter == null) {
         File file1 = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
         File file2 = new File(file1, uuid + ".json");

         serverstatscounter = new ServerStatsCounter(this.server, file2);
         this.stats.put(uuid, serverstatscounter);
      }

      return serverstatscounter;
   }

   public ServerStatsCounter getPlayerStats(UUID uuid, String displayName) {
      ServerPlayer player = getPlayer(uuid);
      ServerStatsCounter serverstatisticsmanager = player == null ? null : (ServerStatsCounter) player.getStats();
      if (serverstatisticsmanager == null) {
         File file1 = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
         File file2 = new File(file1, uuid + ".json");
         if (!file2.exists()) {
            File file3 = new File(file1, displayName + ".json"); // CraftBukkit
            if (file3.exists() && file3.isFile()) {
               file3.renameTo(file2);
            }
         }

         serverstatisticsmanager = new ServerStatsCounter(this.server, file2);
         this.stats.put(uuid, serverstatisticsmanager);
      }

      return serverstatisticsmanager;
   }

   public PlayerAdvancements getPlayerAdvancements(ServerPlayer p_11297_) {
      UUID uuid = p_11297_.getUUID();
      PlayerAdvancements playeradvancements = this.advancements.get(uuid);
      if (playeradvancements == null) {
         Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json");
         playeradvancements = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, p_11297_);
         this.advancements.put(uuid, playeradvancements);
      }

      // Forge: don't overwrite active player with a fake one.
      if (!(p_11297_ instanceof FakePlayer))
      playeradvancements.setPlayer(p_11297_);
      return playeradvancements;
   }

   public void setViewDistance(int p_11218_) {
      this.viewDistance = p_11218_;
      this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(p_11218_));

      for(ServerLevel serverlevel : this.server.getAllLevels()) {
         if (serverlevel != null) {
            serverlevel.getChunkSource().setViewDistance(p_11218_);
         }
      }

   }

   public void setSimulationDistance(int p_184212_) {
      this.simulationDistance = p_184212_;
      this.broadcastAll(new ClientboundSetSimulationDistancePacket(p_184212_));

      for(ServerLevel serverlevel : this.server.getAllLevels()) {
         if (serverlevel != null) {
            serverlevel.getChunkSource().setSimulationDistance(p_184212_);
         }
      }

   }

   public List<ServerPlayer> getPlayers() {
      return this.playersView; //Unmodifiable view, we don't want people removing things without us knowing.
   }

   @Nullable
   public ServerPlayer getPlayer(UUID p_11260_) {
      return this.playersByUUID.get(p_11260_);
   }

   public boolean canBypassPlayerLimit(GameProfile p_11298_) {
      return false;
   }

   public void reloadResources() {
      for (ServerPlayer player : players) {
          player.getAdvancements().reload(this.server.getAdvancements());
          player.getAdvancements().flushDirty(player); // CraftBukkit - trigger immediate flush of advancements
       }

      MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(this, null));
      this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
      ClientboundUpdateRecipesPacket clientboundupdaterecipespacket = new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getRecipes());

      for(ServerPlayer serverplayer : this.players) {
         serverplayer.connection.send(clientboundupdaterecipespacket);
         serverplayer.getRecipeBook().sendInitialRecipeBook(serverplayer);
      }

   }

   public boolean isAllowCheatsForAllPlayers() {
      return this.allowCheatsForAllPlayers;
   }

   public boolean addPlayer(ServerPlayer player) {
      return players.add(player);
   }

   public boolean removePlayer(ServerPlayer player) {
       return this.players.remove(player);
   }
}
