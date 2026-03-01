package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.oneworldstudiomc.api.ServerAPI;
import com.oneworldstudiomc.bukkit.LoginHandler;
import com.oneworldstudiomc.bukkit.inventory.MohistModsInventory;
import com.oneworldstudiomc.paper.event.player.PlayerJumpEvent;
import com.oneworldstudiomc.paper.event.player.PlayerPickItemEvent;
import com.oneworldstudiomc.paper.event.player.PlayerUseUnknownEntityEvent;
import com.oneworldstudiomc.paper.event.server.AsyncTabCompleteEvent;
import com.oneworldstudiomc.plugins.ban.bans.BanItem;
import com.oneworldstudiomc.util.ChatPatchFix;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.oneworldstudiomc.paper.adventure.PaperAdventure;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FutureChain;
import net.minecraft.util.Mth;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftLocation;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.util.LazyPlayerSet;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.SmithingInventory;
import org.slf4j.Logger;
import org.spigotmc.AsyncCatcher;

public class ServerGamePacketListenerImpl implements ServerPlayerConnection, TickablePacketListener, ServerGamePacketListener {
   static final Logger LOGGER = LogUtils.getLogger();
   private static final int LATENCY_CHECK_INTERVAL = 15000;
   /**
    * Forge: Deprecated in favor of range/reach attributes.
    * @see net.minecraftforge.common.ForgeMod#BLOCK_REACH
    * @see net.minecraftforge.common.ForgeMod#ENTITY_REACH
    */
   @Deprecated
   public static final double MAX_INTERACTION_DISTANCE = Mth.square(6.0D);
   private static final int NO_BLOCK_UPDATES_TO_ACK = -1;
   private static final int TRACKED_MESSAGE_DISCONNECT_THRESHOLD = 4096;
   private static final Component CHAT_VALIDATION_FAILED = Component.translatable("multiplayer.disconnect.chat_validation_failed");
   public final Connection connection;
   public final MinecraftServer server;
   public ServerPlayer player;
   private int tickCount;
   private int ackBlockChangesUpTo = -1;
   private long keepAliveTime;
   private boolean keepAlivePending;
   private long keepAliveChallenge;
   // CraftBukkit start - multithreaded fields
   private final AtomicInteger chatSpamTickCount = new AtomicInteger();
   // CraftBukkit end
   private int dropSpamTickCount;
   private double firstGoodX;
   private double firstGoodY;
   private double firstGoodZ;
   private double lastGoodX;
   private double lastGoodY;
   private double lastGoodZ;
   @Nullable
   private Entity lastVehicle;
   private double vehicleFirstGoodX;
   private double vehicleFirstGoodY;
   private double vehicleFirstGoodZ;
   private double vehicleLastGoodX;
   private double vehicleLastGoodY;
   private double vehicleLastGoodZ;
   @Nullable
   private Vec3 awaitingPositionFromClient;
   private int awaitingTeleport;
   private int awaitingTeleportTime;
   private boolean clientIsFloating;
   private int aboveGroundTickCount;
   private boolean clientVehicleIsFloating;
   private int aboveGroundVehicleTickCount;
   private int receivedMovePacketCount;
   private int knownMovePacketCount;
   private final AtomicReference<Instant> lastChatTimeStamp = new AtomicReference<>(Instant.EPOCH);
   @Nullable
   private RemoteChatSession chatSession;
   private SignedMessageChain.Decoder signedMessageDecoder;
   private final LastSeenMessagesValidator lastSeenMessages = new LastSeenMessagesValidator(20);
   private final MessageSignatureCache messageSignatureCache = MessageSignatureCache.createDefault();
   private final FutureChain chatMessageChain;

   public ServerGamePacketListenerImpl(MinecraftServer p_9770_, Connection p_9771_, ServerPlayer p_9772_) {
      this.server = p_9770_;
      this.connection = p_9771_;
      p_9771_.setListener(this);
      this.player = p_9772_;
      p_9772_.connection = this;
      this.keepAliveTime = Util.getMillis();
      p_9772_.getTextFilter().join();
      this.signedMessageDecoder = p_9770_.enforceSecureProfile() ? SignedMessageChain.Decoder.REJECT_ALL : SignedMessageChain.Decoder.unsigned(p_9772_.getUUID());
      this.chatMessageChain = new FutureChain(p_9770_.chatExecutor); // CraftBukkit - async chat
      // CraftBukkit start - add fields and methods
      this.cserver = p_9770_.server;
   }

   private final CraftServer cserver;
   public boolean processedDisconnect;
   private int lastTick = MinecraftServer.currentTick;
   private int allowedPlayerTicks = 1;
   private int lastDropTick = MinecraftServer.currentTick;
   private int lastBookTick  = MinecraftServer.currentTick;
   private int dropCount = 0;

   // Get position of last block hit for BlockDamageLevel.STOPPED
   private double lastPosX = Double.MAX_VALUE;
   private double lastPosY = Double.MAX_VALUE;
   private double lastPosZ = Double.MAX_VALUE;
   private float lastPitch = Float.MAX_VALUE;
   private float lastYaw = Float.MAX_VALUE;
   private boolean justTeleported = false;
   private boolean hasMoved; // Spigot

   public CraftPlayer getCraftPlayer() {
      return (this.player == null) ? null : (CraftPlayer) this.player.getBukkitEntity();
   }

   public void tick() {
      if (this.ackBlockChangesUpTo > -1) {
         this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
         this.ackBlockChangesUpTo = -1;
      }

      this.resetPosition();
      this.player.xo = this.player.getX();
      this.player.yo = this.player.getY();
      this.player.zo = this.player.getZ();
      this.player.doTick();
      this.player.absMoveTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.getYRot(), this.player.getXRot());
      ++this.tickCount;
      this.knownMovePacketCount = this.receivedMovePacketCount;
      if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger() && !this.player.isDeadOrDying()) {
         if (++this.aboveGroundTickCount > 80) {
            LOGGER.warn("{} was kicked for floating too long!", (Object)this.player.getName().getString());
            this.disconnect(Component.translatable("multiplayer.disconnect.flying"));
            return;
         }
      } else {
         this.clientIsFloating = false;
         this.aboveGroundTickCount = 0;
      }

      this.lastVehicle = this.player.getRootVehicle();
      if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
         this.vehicleFirstGoodX = this.lastVehicle.getX();
         this.vehicleFirstGoodY = this.lastVehicle.getY();
         this.vehicleFirstGoodZ = this.lastVehicle.getZ();
         this.vehicleLastGoodX = this.lastVehicle.getX();
         this.vehicleLastGoodY = this.lastVehicle.getY();
         this.vehicleLastGoodZ = this.lastVehicle.getZ();
         if (this.clientVehicleIsFloating && this.player.getRootVehicle().getControllingPassenger() == this.player) {
            if (++this.aboveGroundVehicleTickCount > 80) {
               LOGGER.warn("{} was kicked for floating a vehicle too long!", (Object)this.player.getName().getString());
               this.disconnect(Component.translatable("multiplayer.disconnect.flying"));
               return;
            }
         } else {
            this.clientVehicleIsFloating = false;
            this.aboveGroundVehicleTickCount = 0;
         }
      } else {
         this.lastVehicle = null;
         this.clientVehicleIsFloating = false;
         this.aboveGroundVehicleTickCount = 0;
      }

      this.server.getProfiler().push("keepAlive");
      long i = Util.getMillis();
      if (i - this.keepAliveTime >= 25000L) { // CraftBukkit
         if (this.keepAlivePending) {
            this.disconnect(Component.translatable("disconnect.timeout"));
         } else {
            this.keepAlivePending = true;
            this.keepAliveTime = i;
            this.keepAliveChallenge = i;
            this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
         }
      }

      this.server.getProfiler().pop();
      for (int spam; (spam = this.chatSpamTickCount.get()) > 0 && !chatSpamTickCount.compareAndSet(spam, spam - 1); ) ;

      if (this.dropSpamTickCount > 0) {
         --this.dropSpamTickCount;
      }

      if (this.player.getLastActionTime() > 0L && this.server.getPlayerIdleTimeout() > 0 && Util.getMillis() - this.player.getLastActionTime() > (long)(this.server.getPlayerIdleTimeout() * 1000 * 60)) {
         this.player.resetLastActionTime(); // CraftBukkit - SPIGOT-854
         this.disconnect(Component.translatable("multiplayer.disconnect.idling"));
      }

   }

   public void resetPosition() {
      this.firstGoodX = this.player.getX();
      this.firstGoodY = this.player.getY();
      this.firstGoodZ = this.player.getZ();
      this.lastGoodX = this.player.getX();
      this.lastGoodY = this.player.getY();
      this.lastGoodZ = this.player.getZ();
   }

   public boolean isAcceptingMessages() {
      return this.connection.isConnected();
   }

   private boolean isSingleplayerOwner() {
      return this.server.isSingleplayerOwner(this.player.getGameProfile());
   }

   // CraftBukkit start
   public void disconnect(Component p_9943_) {
      disconnect(CraftChatMessage.fromComponent(p_9943_));
   }
   // CraftBukkit end

   public void disconnect(String pTextComponent) {
      // CraftBukkit start - fire PlayerKickEvent
      if (this.processedDisconnect) {
         return;
      }
      if (!this.cserver.isPrimaryThread()) {
         LoginHandler.disconnect(this, pTextComponent);
         return;
      }
      String leaveMessage = ChatFormatting.YELLOW + this.player.getScoreboardName() + " left the game.";

      PlayerKickEvent event = new PlayerKickEvent(this.player.getBukkitEntity(), pTextComponent, leaveMessage);

      if (this.cserver.getServer().isRunning()) {
         this.cserver.getPluginManager().callEvent(event);
      }

      if (event.isCancelled()) {
         // Do not kick the player
         return;
      }
      this.player.kickLeaveMessage = event.getLeaveMessage(); // CraftBukkit - SPIGOT-3034: Forward leave message to PlayerQuitEvent
      // Send the possibly modified leave message
      final Component ichatbasecomponent = CraftChatMessage.fromString(event.getReason(), true)[0];
      // CraftBukkit end
      this.connection.send(new ClientboundDisconnectPacket(ichatbasecomponent), PacketSendListener.thenRun(() -> {
         this.connection.disconnect(ichatbasecomponent);
      }));

      this.onDisconnect(ichatbasecomponent); // CraftBukkit - fire quit instantly
      this.connection.setReadOnly();
      this.server.wrapRunnable(this.connection::handleDisconnection); // CraftBukkit - Don't wait
   }

   private <T, R> CompletableFuture<R> filterTextPacket(T p_243240_, BiFunction<TextFilter, T, CompletableFuture<R>> p_243271_) {
      return p_243271_.apply(this.player.getTextFilter(), p_243240_).thenApply((p_264862_) -> {
         if (!this.isAcceptingMessages()) {
            LOGGER.debug("Ignoring packet due to disconnection");
            throw new CancellationException("disconnected");
         } else {
            return p_264862_;
         }
      });
   }

   private CompletableFuture<FilteredText> filterTextPacket(String p_243213_) {
      return this.filterTextPacket(p_243213_, TextFilter::processStreamMessage);
   }

   private CompletableFuture<List<FilteredText>> filterTextPacket(List<String> p_243258_) {
      return this.filterTextPacket(p_243258_, TextFilter::processMessageBundle);
   }

   public void handlePlayerInput(ServerboundPlayerInputPacket p_9893_) {
      PacketUtils.ensureRunningOnSameThread(p_9893_, this, this.player.serverLevel());
      this.player.setPlayerInput(p_9893_.getXxa(), p_9893_.getZza(), p_9893_.isJumping(), p_9893_.isShiftKeyDown());
   }

   private static boolean containsInvalidValues(double p_143664_, double p_143665_, double p_143666_, float p_143667_, float p_143668_) {
      return Double.isNaN(p_143664_) || Double.isNaN(p_143665_) || Double.isNaN(p_143666_) || !Floats.isFinite(p_143668_) || !Floats.isFinite(p_143667_);
   }

   private static double clampHorizontal(double p_143610_) {
      return Mth.clamp(p_143610_, -3.0E7D, 3.0E7D);
   }

   private static double clampVertical(double p_143654_) {
      return Mth.clamp(p_143654_, -2.0E7D, 2.0E7D);
   }

   public void handleMoveVehicle(ServerboundMoveVehiclePacket p_9876_) {
      PacketUtils.ensureRunningOnSameThread(p_9876_, this, this.player.serverLevel());
      if (containsInvalidValues(p_9876_.getX(), p_9876_.getY(), p_9876_.getZ(), p_9876_.getYRot(), p_9876_.getXRot())) {
         this.disconnect(Component.translatable("multiplayer.disconnect.invalid_vehicle_movement"));
      } else {
         Entity entity = this.player.getRootVehicle();
         if (entity != this.player && entity.getControllingPassenger() == this.player && entity == this.lastVehicle) {
            ServerLevel serverlevel = this.player.serverLevel();
            double d0 = entity.getX();
            double d1 = entity.getY();
            double d2 = entity.getZ();
            double d3 = clampHorizontal(p_9876_.getX());
            double d4 = clampVertical(p_9876_.getY());
            double d5 = clampHorizontal(p_9876_.getZ());
            float f = Mth.wrapDegrees(p_9876_.getYRot());
            float f1 = Mth.wrapDegrees(p_9876_.getXRot());
            double d6 = d3 - this.vehicleFirstGoodX;
            double d7 = d4 - this.vehicleFirstGoodY;
            double d8 = d5 - this.vehicleFirstGoodZ;
            double d9 = entity.getDeltaMovement().lengthSqr();
            double d10 = d6 * d6 + d7 * d7 + d8 * d8;
            // CraftBukkit start - handle custom speeds and skipped ticks
            this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
            this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
            this.lastTick = (int) (System.currentTimeMillis() / 50);

            ++this.receivedMovePacketCount;
            int i = this.receivedMovePacketCount - this.knownMovePacketCount;
            if (i > Math.max(this.allowedPlayerTicks, 5)) {
               LOGGER.debug(this.player.getScoreboardName() + " is sending move packets too frequently (" + i + " packets since last tick)");
               i = 1;
            }

            if (d10 > 0) {
               allowedPlayerTicks -= 1;
            } else {
               allowedPlayerTicks = 20;
            }
            double speed;
            if (player.getAbilities().flying) {
               speed = player.getAbilities().flyingSpeed * 20f;
            } else {
               speed = player.getAbilities().walkingSpeed * 10f;
            }
            speed *= 2f; // TODO: Get the speed of the vehicle instead of the player
            if (d10 - d9 > Math.max(100.0D, Math.pow((double) (org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
               // CraftBukkit end
               // LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", entity.getName().getString(), this.player.getName().getString(), d6, d7, d8); // Mohist
               this.connection.send(new ClientboundMoveVehiclePacket(entity));
               return;
            }

            boolean flag = serverlevel.noCollision(entity, entity.getBoundingBox().deflate(0.0625D));
            d6 = d3 - this.vehicleLastGoodX;
            d7 = d4 - this.vehicleLastGoodY - 1.0E-6D;
            d8 = d5 - this.vehicleLastGoodZ;
            boolean flag1 = entity.verticalCollisionBelow;
            if (entity instanceof LivingEntity) {
               LivingEntity livingentity = (LivingEntity)entity;
               if (livingentity.onClimbable()) {
                  livingentity.resetFallDistance();
               }
            }

            entity.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
            d6 = d3 - entity.getX();
            d7 = d4 - entity.getY();
            if (d7 > -0.5D || d7 < 0.5D) {
               d7 = 0.0D;
            }

            d8 = d5 - entity.getZ();
            d10 = d6 * d6 + d7 * d7 + d8 * d8;
            boolean flag2 = false;
            if (d10 > org.spigotmc.SpigotConfig.movedWronglyThreshold) { // Spigot
               flag2 = true;
               // LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", entity.getName().getString(), this.player.getName().getString(), Math.sqrt(d10));
            }
            Location curPos = this.getCraftPlayer().getLocation(); // Spigot

            entity.absMoveTo(d3, d4, d5, this.player.getYRot(), this.player.getXRot());
            boolean flag3 = serverlevel.noCollision(entity, entity.getBoundingBox().deflate(0.0625D));
            if (flag && (flag2 || !flag3)) {
               entity.absMoveTo(d0, d1, d2, this.player.getYRot(), this.player.getXRot());
               this.connection.send(new ClientboundMoveVehiclePacket(entity));
               return;
            }

            // CraftBukkit start - fire PlayerMoveEvent
            org.bukkit.entity.Player player = this.getCraftPlayer();
            // Spigot Start
            if ( !hasMoved )
            {
               lastPosX = curPos.getX();
               lastPosY = curPos.getY();
               lastPosZ = curPos.getZ();
               lastYaw = curPos.getYaw();
               lastPitch = curPos.getPitch();
               hasMoved = true;
            }
            // Spigot End
            Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
            Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

            // If the packet contains movement information then we update the To location with the correct XYZ.
            to.setX(p_9876_.getX());
            to.setY(p_9876_.getY());
            to.setZ(p_9876_.getZ());


            // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
            to.setYaw(p_9876_.getYRot());
            to.setPitch(p_9876_.getXRot());

            // Prevent 40 event-calls for less than a single pixel of movement >.>
            double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
            float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

            if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
               this.lastPosX = to.getX();
               this.lastPosY = to.getY();
               this.lastPosZ = to.getZ();
               this.lastYaw = to.getYaw();
               this.lastPitch = to.getPitch();

               // Skip the first time we do this
               if (from.getX() != Double.MAX_VALUE) {
                  Location oldTo = to.clone();
                  PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                  this.cserver.getPluginManager().callEvent(event);

                  // If the event is cancelled we move the player back to their old location.
                  if (event.isCancelled()) {
                     teleport(from);
                     return;
                  }

                  // If a Plugin has changed the To destination then we teleport the Player
                  // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                  // We only do this if the Event was not cancelled.
                  if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                     this.player.getBukkitEntity().teleport(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                     return;
                  }

                  // Check to see if the Players Location has some how changed during the call of the event.
                  // This can happen due to a plugin teleporting the player instead of using .setTo()
                  if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                     this.justTeleported = false;
                     return;
                  }
               }
            }
            // CraftBukkit end

            this.player.serverLevel().getChunkSource().move(this.player);
            this.player.checkMovementStatistics(this.player.getX() - d0, this.player.getY() - d1, this.player.getZ() - d2);
            this.clientVehicleIsFloating = d7 >= -0.03125D && !flag1 && !this.server.isFlightAllowed() && !entity.isNoGravity() && this.noBlocksAround(entity);
            this.vehicleLastGoodX = entity.getX();
            this.vehicleLastGoodY = entity.getY();
            this.vehicleLastGoodZ = entity.getZ();
         }

      }
   }

   private boolean noBlocksAround(Entity p_9794_) {
      return p_9794_.level().getBlockStates(p_9794_.getBoundingBox().inflate(0.0625D).expandTowards(0.0D, -0.55D, 0.0D)).allMatch(BlockBehaviour.BlockStateBase::isAir);
   }

   public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket p_9835_) {
      PacketUtils.ensureRunningOnSameThread(p_9835_, this, this.player.serverLevel());
      if (p_9835_.getId() == this.awaitingTeleport) {
         if (this.awaitingPositionFromClient == null) {
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"));
            return;
         }

         this.player.absMoveTo(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot());
         this.lastGoodX = this.awaitingPositionFromClient.x;
         this.lastGoodY = this.awaitingPositionFromClient.y;
         this.lastGoodZ = this.awaitingPositionFromClient.z;
         if (this.player.isChangingDimension()) {
            this.player.hasChangedDimension();
         }

         this.awaitingPositionFromClient = null;
         this.player.serverLevel().getChunkSource().move(this.player); // CraftBukkit
      }

   }

   public void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket p_9897_) {
      PacketUtils.ensureRunningOnSameThread(p_9897_, this, this.player.serverLevel());
      this.server.getRecipeManager().byKey(p_9897_.getRecipe()).ifPresent(this.player.getRecipeBook()::removeHighlight);
   }

   public void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket p_9895_) {
      PacketUtils.ensureRunningOnSameThread(p_9895_, this, this.player.serverLevel());
      this.player.getRecipeBook().setBookSetting(p_9895_.getBookType(), p_9895_.isOpen(), p_9895_.isFiltering());
   }

   public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket p_9903_) {
      PacketUtils.ensureRunningOnSameThread(p_9903_, this, this.player.serverLevel());
      if (p_9903_.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
         ResourceLocation resourcelocation = p_9903_.getTab();
         Advancement advancement = this.server.getAdvancements().getAdvancement(resourcelocation);
         if (advancement != null) {
            this.player.getAdvancements().setSelectedTab(advancement);
         }
      }

   }

   // Paper start
   private static final java.util.concurrent.ExecutorService TAB_COMPLETE_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(4,
           new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon(true).setNameFormat("Async Tab Complete Thread - #%d").setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER)).build());
   // Paper end

   public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket p_9847_) {
      // PacketUtils.ensureRunningOnSameThread(pPacket, this, this.player.serverLevel()); // Paper - run this async
      // CraftBukkit start
      if (chatSpamTickCount.addAndGet(1) > 500 && !this.server.getPlayerList().isOp(this.player.getGameProfile())) {
         this.disconnect(Component.translatable("disconnect.spam"));
         server.scheduleOnMain(() -> ServerGamePacketListenerImpl.this.disconnect(Component.translatable("disconnect.spam", new Object[0]))); // Paper
         return;
      }
      // CraftBukkit end
      // Paper start - async tab completion
      TAB_COMPLETE_EXECUTOR.execute(new Runnable() {
          @Override
          public void run() {
              StringReader stringreader = new StringReader(p_9847_.getCommand());
              if (stringreader.canRead() && stringreader.peek() == '/') {
                  stringreader.skip();
              }

              final String command = p_9847_.getCommand();
              final AsyncTabCompleteEvent event = new AsyncTabCompleteEvent(ServerGamePacketListenerImpl.this.getCraftPlayer(), command, true, null);
              event.callEvent();
              final List<AsyncTabCompleteEvent.Completion> completions = event.isCancelled() ? com.google.common.collect.ImmutableList.of() : event.completions();
              // If the event isn't handled, we can assume that we have no completions, and so we'll ask the server
              if (!event.isHandled()) {
                  if (!event.isCancelled()) {

                      ServerGamePacketListenerImpl.this.server.scheduleOnMain(new Runnable() {
                          @Override
                          public void run() { // This needs to be on main
                              ParseResults<CommandSourceStack> parseresults = ServerGamePacketListenerImpl.this.server.getCommands().getDispatcher().parse(stringreader, ServerGamePacketListenerImpl.this.player.createCommandSourceStack());

                              ServerGamePacketListenerImpl.this.server.getCommands().getDispatcher().getCompletionSuggestions(parseresults).thenAccept((p_238197_) -> {
                                  if (p_238197_.isEmpty())
                                      return; // CraftBukkit - don't send through empty suggestions - prevents [<args>] from showing for plugins with nothing more to offer
                                  ServerGamePacketListenerImpl.this.connection.send(new ClientboundCommandSuggestionsPacket(p_9847_.getId(), p_238197_));
                              });
                          }
                      });
                  }
              } else if (!completions.isEmpty()) {
                  final com.mojang.brigadier.suggestion.SuggestionsBuilder builder0 = new com.mojang.brigadier.suggestion.SuggestionsBuilder(command, stringreader.getTotalLength());
                  final com.mojang.brigadier.suggestion.SuggestionsBuilder builder = builder0.createOffset(builder0.getInput().lastIndexOf(' ') + 1);
                  completions.forEach(new Consumer<AsyncTabCompleteEvent.Completion>() {
                      @Override
                      public void accept(AsyncTabCompleteEvent.Completion completion) {
                          final Integer intSuggestion = com.google.common.primitives.Ints.tryParse(completion.suggestion());
                          if (intSuggestion != null) {
                              builder.suggest(intSuggestion, PaperAdventure.asVanilla(completion.tooltip()));
                          } else {
                              builder.suggest(completion.suggestion(), PaperAdventure.asVanilla(completion.tooltip()));
                          }
                      }
                  });
                  player.connection.send(new ClientboundCommandSuggestionsPacket(p_9847_.getId(), builder.buildFuture().join()));
              }
          }
      });
   }

   public void handleSetCommandBlock(ServerboundSetCommandBlockPacket p_9911_) {
      PacketUtils.ensureRunningOnSameThread(p_9911_, this, this.player.serverLevel());
      if (!this.server.isCommandBlockEnabled()) {
         this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
      } else if (!this.player.canUseGameMasterBlocks()) {
         this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
      } else {
         BaseCommandBlock basecommandblock = null;
         CommandBlockEntity commandblockentity = null;
         BlockPos blockpos = p_9911_.getPos();
         BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);
         if (blockentity instanceof CommandBlockEntity) {
            commandblockentity = (CommandBlockEntity)blockentity;
            basecommandblock = commandblockentity.getCommandBlock();
         }

         String s = p_9911_.getCommand();
         boolean flag = p_9911_.isTrackOutput();
         if (basecommandblock != null) {
            CommandBlockEntity.Mode commandblockentity$mode = commandblockentity.getMode();
            BlockState blockstate = this.player.level().getBlockState(blockpos);
            Direction direction = blockstate.getValue(CommandBlock.FACING);
            BlockState blockstate1;
            switch (p_9911_.getMode()) {
               case SEQUENCE:
                  blockstate1 = Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
                  break;
               case AUTO:
                  blockstate1 = Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
                  break;
               case REDSTONE:
               default:
                  blockstate1 = Blocks.COMMAND_BLOCK.defaultBlockState();
            }

            BlockState blockstate2 = blockstate1.setValue(CommandBlock.FACING, direction).setValue(CommandBlock.CONDITIONAL, Boolean.valueOf(p_9911_.isConditional()));
            if (blockstate2 != blockstate) {
               this.player.level().setBlock(blockpos, blockstate2, 2);
               blockentity.setBlockState(blockstate2);
               this.player.level().getChunkAt(blockpos).setBlockEntity(blockentity);
            }

            basecommandblock.setCommand(s);
            basecommandblock.setTrackOutput(flag);
            if (!flag) {
               basecommandblock.setLastOutput((Component)null);
            }

            commandblockentity.setAutomatic(p_9911_.isAutomatic());
            if (commandblockentity$mode != p_9911_.getMode()) {
               commandblockentity.onModeSwitch();
            }

            basecommandblock.onUpdated();
            if (!StringUtil.isNullOrEmpty(s)) {
               this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", s));
            }
         }

      }
   }

   public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket p_9913_) {
      PacketUtils.ensureRunningOnSameThread(p_9913_, this, this.player.serverLevel());
      if (!this.server.isCommandBlockEnabled()) {
         this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
      } else if (!this.player.canUseGameMasterBlocks()) {
         this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
      } else {
         BaseCommandBlock basecommandblock = p_9913_.getCommandBlock(this.player.level());
         if (basecommandblock != null) {
            basecommandblock.setCommand(p_9913_.getCommand());
            basecommandblock.setTrackOutput(p_9913_.isTrackOutput());
            if (!p_9913_.isTrackOutput()) {
               basecommandblock.setLastOutput((Component)null);
            }

            basecommandblock.onUpdated();
            this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", p_9913_.getCommand()));
         }

      }
   }

   public void handlePickItem(ServerboundPickItemPacket p_9880_) {
      PacketUtils.ensureRunningOnSameThread(p_9880_, this, this.player.serverLevel());
      // Paper start - validate pick item position
      if (!(p_9880_.getSlot() >= 0 && p_9880_.getSlot() < this.player.getInventory().items.size())) {
         ServerGamePacketListenerImpl.LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
         this.disconnect("Invalid hotbar selection (Hacking?)");
         return;
      }
      org.bukkit.entity.Player bukkitPlayer = this.player.getBukkitEntity();
      int targetSlot = this.player.getInventory().getSuitableHotbarSlot();
      int sourceSlot = p_9880_.getSlot();

      PlayerPickItemEvent event = new PlayerPickItemEvent(bukkitPlayer, targetSlot, sourceSlot);
      if (!event.callEvent()) return;

      this.player.getInventory().pickSlot(event.getSourceSlot(), event.getTargetSlot());
      // Paper end
      this.player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, this.player.getInventory().selected, this.player.getInventory().getItem(this.player.getInventory().selected)));
      this.player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, p_9880_.getSlot(), this.player.getInventory().getItem(p_9880_.getSlot())));
      this.player.connection.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
   }

   public void handleRenameItem(ServerboundRenameItemPacket p_9899_) {
      PacketUtils.ensureRunningOnSameThread(p_9899_, this, this.player.serverLevel());
      AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;
      if (abstractcontainermenu instanceof AnvilMenu anvilmenu) {
         if (!anvilmenu.stillValid(this.player)) {
            LOGGER.debug("Player {} interacted with invalid menu {}", this.player, anvilmenu);
            return;
         }

         anvilmenu.setItemName(p_9899_.getName());
      }

   }

   public void handleSetBeaconPacket(ServerboundSetBeaconPacket p_9907_) {
      PacketUtils.ensureRunningOnSameThread(p_9907_, this, this.player.serverLevel());
      AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;
      if (abstractcontainermenu instanceof BeaconMenu beaconmenu) {
         if (!this.player.containerMenu.stillValid(this.player)) {
            LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            return;
         }

         beaconmenu.updateEffects(p_9907_.getPrimary(), p_9907_.getSecondary());
      }

   }

   public void handleSetStructureBlock(ServerboundSetStructureBlockPacket p_9919_) {
      PacketUtils.ensureRunningOnSameThread(p_9919_, this, this.player.serverLevel());
      if (this.player.canUseGameMasterBlocks()) {
         BlockPos blockpos = p_9919_.getPos();
         BlockState blockstate = this.player.level().getBlockState(blockpos);
         BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);
         if (blockentity instanceof StructureBlockEntity) {
            StructureBlockEntity structureblockentity = (StructureBlockEntity)blockentity;
            structureblockentity.setMode(p_9919_.getMode());
            structureblockentity.setStructureName(p_9919_.getName());
            structureblockentity.setStructurePos(p_9919_.getOffset());
            structureblockentity.setStructureSize(p_9919_.getSize());
            structureblockentity.setMirror(p_9919_.getMirror());
            structureblockentity.setRotation(p_9919_.getRotation());
            structureblockentity.setMetaData(p_9919_.getData());
            structureblockentity.setIgnoreEntities(p_9919_.isIgnoreEntities());
            structureblockentity.setShowAir(p_9919_.isShowAir());
            structureblockentity.setShowBoundingBox(p_9919_.isShowBoundingBox());
            structureblockentity.setIntegrity(p_9919_.getIntegrity());
            structureblockentity.setSeed(p_9919_.getSeed());
            if (structureblockentity.hasStructureName()) {
               String s = structureblockentity.getStructureName();
               if (p_9919_.getUpdateType() == StructureBlockEntity.UpdateType.SAVE_AREA) {
                  if (structureblockentity.saveStructure()) {
                     this.player.displayClientMessage(Component.translatable("structure_block.save_success", s), false);
                  } else {
                     this.player.displayClientMessage(Component.translatable("structure_block.save_failure", s), false);
                  }
               } else if (p_9919_.getUpdateType() == StructureBlockEntity.UpdateType.LOAD_AREA) {
                  if (!structureblockentity.isStructureLoadable()) {
                     this.player.displayClientMessage(Component.translatable("structure_block.load_not_found", s), false);
                  } else if (structureblockentity.loadStructure(this.player.serverLevel())) {
                     this.player.displayClientMessage(Component.translatable("structure_block.load_success", s), false);
                  } else {
                     this.player.displayClientMessage(Component.translatable("structure_block.load_prepare", s), false);
                  }
               } else if (p_9919_.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
                  if (structureblockentity.detectSize()) {
                     this.player.displayClientMessage(Component.translatable("structure_block.size_success", s), false);
                  } else {
                     this.player.displayClientMessage(Component.translatable("structure_block.size_failure"), false);
                  }
               }
            } else {
               this.player.displayClientMessage(Component.translatable("structure_block.invalid_structure_name", p_9919_.getName()), false);
            }

            structureblockentity.setChanged();
            this.player.level().sendBlockUpdated(blockpos, blockstate, blockstate, 3);
         }

      }
   }

   public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket p_9917_) {
      PacketUtils.ensureRunningOnSameThread(p_9917_, this, this.player.serverLevel());
      if (this.player.canUseGameMasterBlocks()) {
         BlockPos blockpos = p_9917_.getPos();
         BlockState blockstate = this.player.level().getBlockState(blockpos);
         BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);
         if (blockentity instanceof JigsawBlockEntity) {
            JigsawBlockEntity jigsawblockentity = (JigsawBlockEntity)blockentity;
            jigsawblockentity.setName(p_9917_.getName());
            jigsawblockentity.setTarget(p_9917_.getTarget());
            jigsawblockentity.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, p_9917_.getPool()));
            jigsawblockentity.setFinalState(p_9917_.getFinalState());
            jigsawblockentity.setJoint(p_9917_.getJoint());
            jigsawblockentity.setChanged();
            this.player.level().sendBlockUpdated(blockpos, blockstate, blockstate, 3);
         }

      }
   }

   public void handleJigsawGenerate(ServerboundJigsawGeneratePacket p_9868_) {
      PacketUtils.ensureRunningOnSameThread(p_9868_, this, this.player.serverLevel());
      if (this.player.canUseGameMasterBlocks()) {
         BlockPos blockpos = p_9868_.getPos();
         BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);
         if (blockentity instanceof JigsawBlockEntity) {
            JigsawBlockEntity jigsawblockentity = (JigsawBlockEntity)blockentity;
            jigsawblockentity.generate(this.player.serverLevel(), p_9868_.levels(), p_9868_.keepJigsaws());
         }

      }
   }

   public void handleSelectTrade(ServerboundSelectTradePacket p_9905_) {
      PacketUtils.ensureRunningOnSameThread(p_9905_, this, this.player.serverLevel());
      int i = p_9905_.getItem();
      AbstractContainerMenu abstractcontainermenu = this.player.containerMenu;
      if (abstractcontainermenu instanceof MerchantMenu merchantmenu) {
         // CraftBukkit start
         final org.bukkit.event.inventory.TradeSelectEvent tradeSelectEvent = CraftEventFactory.callTradeSelectEvent(this.player, i, merchantmenu);
         if (tradeSelectEvent.isCancelled()) {
            this.player.getBukkitEntity().updateInventory();
            return;
         }
         // CraftBukkit end

         if (!merchantmenu.stillValid(this.player)) {
            LOGGER.debug("Player {} interacted with invalid menu {}", this.player, merchantmenu);
            return;
         }

         merchantmenu.setSelectionHint(i);
         merchantmenu.tryMoveItems(i);
      }

   }

   public void handleEditBook(ServerboundEditBookPacket p_9862_) {
      // CraftBukkit start
      if (this.lastBookTick + 20 > MinecraftServer.currentTick) {
         this.disconnect("Book edited too quickly!");
         return;
      }
      this.lastBookTick = MinecraftServer.currentTick;
      // CraftBukkit end
      int i = p_9862_.getSlot();
      if (Inventory.isHotbarSlot(i) || i == 40) {
         List<String> list = Lists.newArrayList();
         Optional<String> optional = p_9862_.getTitle();
         optional.ifPresent(list::add);
         p_9862_.getPages().stream().limit(100L).forEach(list::add);
         Consumer<List<FilteredText>> consumer = optional.isPresent() ? (p_238198_) -> {
            this.signBook(p_238198_.get(0), p_238198_.subList(1, p_238198_.size()), i);
         } : (p_143627_) -> {
            this.updateBookContents(p_143627_, i);
         };
         this.filterTextPacket(list).thenAcceptAsync(consumer, this.server);
      }
   }

   private void updateBookContents(List<FilteredText> p_9813_, int p_9814_) {
      ItemStack itemstack = this.player.getInventory().getItem(p_9814_);
      if (itemstack.is(Items.WRITABLE_BOOK)) {
         mohist$slot.set(p_9814_);
         mohist$handItem.set(itemstack);
         this.updateBookPages(p_9813_, UnaryOperator.identity(), itemstack);
      }
   }

   private void signBook(FilteredText p_215209_, List<FilteredText> p_215210_, int p_215211_) {
      ItemStack itemstack = this.player.getInventory().getItem(p_215211_);
      if (itemstack.is(Items.WRITABLE_BOOK)) {
         ItemStack itemstack1 = new ItemStack(Items.WRITTEN_BOOK);
         CompoundTag compoundtag = itemstack.getTag();
         if (compoundtag != null) {
            itemstack1.setTag(compoundtag.copy());
         }

         itemstack1.addTagElement("author", StringTag.valueOf(this.player.getName().getString()));
         if (this.player.isTextFilteringEnabled()) {
            itemstack1.addTagElement("title", StringTag.valueOf(p_215209_.filteredOrEmpty()));
         } else {
            itemstack1.addTagElement("filtered_title", StringTag.valueOf(p_215209_.filteredOrEmpty()));
            itemstack1.addTagElement("title", StringTag.valueOf(p_215209_.raw()));
         }
         mohist$slot.set(p_215211_);
         mohist$handItem.set(itemstack);
         this.updateBookPages(p_215210_, (p_238206_) -> {
            return Component.Serializer.toJson(Component.literal(p_238206_));
         }, itemstack1);
         this.player.getInventory().setItem(p_215211_, itemstack); // CraftBukkit - event factory updates the hand book
      }
   }

   // Mohist start
   private AtomicInteger mohist$slot = new AtomicInteger(-1);
   private AtomicReference<ItemStack> mohist$handItem = new AtomicReference<>(null);

   private void updateBookPages(List<FilteredText> pPages, UnaryOperator<String> pUpdater, ItemStack pBook, int slot, ItemStack handItem) { // CraftBukkit
      mohist$slot.set(slot);
      mohist$handItem.set(handItem);
      updateBookPages(pPages, pUpdater, pBook);
   }

   private void updateBookPages(List<FilteredText> p_143635_, UnaryOperator<String> p_143636_, ItemStack p_143637_) {
      ListTag listtag = new ListTag();
      if (this.player.isTextFilteringEnabled()) {
         p_143635_.stream().map((p_238209_) -> {
            return StringTag.valueOf(p_143636_.apply(p_238209_.filteredOrEmpty()));
         }).forEach(listtag::add);
      } else {
         CompoundTag compoundtag = new CompoundTag();
         int i = 0;

         for(int j = p_143635_.size(); i < j; ++i) {
            FilteredText filteredtext = p_143635_.get(i);
            String s = filteredtext.raw();
            listtag.add(StringTag.valueOf(p_143636_.apply(s)));
            if (filteredtext.isFiltered()) {
               compoundtag.putString(String.valueOf(i), p_143636_.apply(filteredtext.filteredOrEmpty()));
            }
         }

         if (!compoundtag.isEmpty()) {
            p_143637_.addTagElement("filtered_pages", compoundtag);
         }
      }

      p_143637_.addTagElement("pages", listtag);
      CraftEventFactory.handleEditBookEvent(player, mohist$slot.getAndSet(-1), mohist$handItem.getAndSet(null), p_143637_); // CraftBukkit
   }
   // Mohist end

   public void handleEntityTagQuery(ServerboundEntityTagQuery p_9864_) {
      PacketUtils.ensureRunningOnSameThread(p_9864_, this, this.player.serverLevel());
      if (this.player.hasPermissions(2)) {
         Entity entity = this.player.level().getEntity(p_9864_.getEntityId());
         if (entity != null) {
            CompoundTag compoundtag = entity.saveWithoutId(new CompoundTag());
            this.player.connection.send(new ClientboundTagQueryPacket(p_9864_.getTransactionId(), compoundtag));
         }

      }
   }

   public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQuery p_9837_) {
      PacketUtils.ensureRunningOnSameThread(p_9837_, this, this.player.serverLevel());
      if (this.player.hasPermissions(2)) {
         BlockEntity blockentity = this.player.level().getBlockEntity(p_9837_.getPos());
         CompoundTag compoundtag = blockentity != null ? blockentity.saveWithoutMetadata() : null;
         this.player.connection.send(new ClientboundTagQueryPacket(p_9837_.getTransactionId(), compoundtag));
      }
   }

   public void handleMovePlayer(ServerboundMovePlayerPacket p_9874_) {
      PacketUtils.ensureRunningOnSameThread(p_9874_, this, this.player.serverLevel());
      if (containsInvalidValues(p_9874_.getX(0.0D), p_9874_.getY(0.0D), p_9874_.getZ(0.0D), p_9874_.getYRot(0.0F), p_9874_.getXRot(0.0F))) {
         this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"));
      } else {
         ServerLevel serverlevel = this.player.serverLevel();
         if (!this.player.wonGame && !this.player.isImmobile()) { // CraftBukkit
            if (this.tickCount == 0) {
               this.resetPosition();
            }

            if (this.awaitingPositionFromClient != null) {
               if (this.tickCount - this.awaitingTeleportTime > 20) {
                  this.awaitingTeleportTime = this.tickCount;
                  this.teleport(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot());
               }

               this.allowedPlayerTicks = 20; // CraftBukkit
            } else {
               this.awaitingTeleportTime = this.tickCount;
               double d0 = clampHorizontal(p_9874_.getX(this.player.getX()));
               double d1 = clampVertical(p_9874_.getY(this.player.getY()));
               double d2 = clampHorizontal(p_9874_.getZ(this.player.getZ()));
               float f = Mth.wrapDegrees(p_9874_.getYRot(this.player.getYRot()));
               float f1 = Mth.wrapDegrees(p_9874_.getXRot(this.player.getXRot()));
               if (this.player.isPassenger()) {
                  this.player.absMoveTo(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                  this.player.serverLevel().getChunkSource().move(this.player);
                  this.allowedPlayerTicks = 20; // CraftBukkit
               } else {
                  // CraftBukkit - Make sure the move is valid but then reset it for plugins to modify
                  double prevX = player.getX();
                  double prevY = player.getY();
                  double prevZ = player.getZ();
                  float prevYaw = player.getYRot();
                  float prevPitch = player.getXRot();
                  // CraftBukkit end
                  double d3 = this.player.getX();
                  double d4 = this.player.getY();
                  double d5 = this.player.getZ();
                  double d6 = d0 - this.firstGoodX;
                  double d7 = d1 - this.firstGoodY;
                  double d8 = d2 - this.firstGoodZ;
                  double d9 = this.player.getDeltaMovement().lengthSqr();
                  double d10 = d6 * d6 + d7 * d7 + d8 * d8;
                  if (this.player.isSleeping()) {
                     if (d10 > 1.0D) {
                        this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                     }

                  } else {
                     ++this.receivedMovePacketCount;
                     int i = this.receivedMovePacketCount - this.knownMovePacketCount;
                     // CraftBukkit start - handle custom speeds and skipped ticks
                     this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                     this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                     this.lastTick = (int) (System.currentTimeMillis() / 50);
                     if (i > Math.max(this.allowedPlayerTicks, 5)) {
                        LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getName().getString(), i);
                        i = 1;
                     }

                     if (p_9874_.hasRot || d10 > 0) {
                        allowedPlayerTicks -= 1;
                     } else {
                        allowedPlayerTicks = 20;
                     }
                     double speed;
                     if (player.getAbilities().flying) {
                        speed = player.getAbilities().flyingSpeed * 20f;
                     } else {
                        speed = player.getAbilities().walkingSpeed * 10f;
                     }

                     if (!this.player.isChangingDimension() && (!this.player.level().getGameRules().getBoolean(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK) || !this.player.isFallFlying())) {
                        float f2 = this.player.isFallFlying() ? 300.0F : 100.0F;
                        if (d10 - d9 > Math.max(f2, Math.pow((double) (10.0F * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
                           // CraftBukkit end
                           LOGGER.warn("{} moved too quickly! {},{},{}", this.player.getName().getString(), d6, d7, d8);
                           this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
                           return;
                        }
                     }

                     AABB aabb = this.player.getBoundingBox();
                     d6 = d0 - this.lastGoodX;
                     d7 = d1 - this.lastGoodY;
                     d8 = d2 - this.lastGoodZ;
                     boolean flag = d7 > 0.0D;
                     if (this.player.onGround() && !p_9874_.isOnGround() && flag) {
                        // Paper start - Add player jump event
                        org.bukkit.entity.Player player = this.getCraftPlayer();
                        Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                        Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                        // If the packet contains movement information then we update the To location with the correct XYZ.
                        if (p_9874_.hasPos) {
                           to.setX(p_9874_.x);
                           to.setY(p_9874_.y);
                           to.setZ(p_9874_.z);
                        }

                        // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                        if (p_9874_.hasRot) {
                           to.setYaw(p_9874_.yRot);
                           to.setPitch(p_9874_.xRot);
                        }

                        PlayerJumpEvent event = new PlayerJumpEvent(player, from, to);

                        if (event.callEvent()) {
                           this.player.jumpFromGround();
                        } else {
                           from = event.getFrom();
                           this.internalTeleport(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch(), Collections.emptySet());
                           return;
                        }
                        // Paper end
                     }

                     boolean flag1 = this.player.verticalCollisionBelow;
                     this.player.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
                     this.player.onGround = p_9874_.isOnGround(); // CraftBukkit - SPIGOT-5810, SPIGOT-5835, SPIGOT-6828: reset by this.player.move
                     d6 = d0 - this.player.getX();
                     d7 = d1 - this.player.getY();
                     if (d7 > -0.5D || d7 < 0.5D) {
                        d7 = 0.0D;
                     }

                     d8 = d2 - this.player.getZ();
                     d10 = d6 * d6 + d7 * d7 + d8 * d8;
                     boolean flag2 = false;
                     if (!this.player.isChangingDimension() && d10 > org.spigotmc.SpigotConfig.movedWronglyThreshold && !this.player.isSleeping() && !this.player.gameMode.isCreative() && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                        flag2 = true;
                        LOGGER.warn("{} moved wrongly!", (Object)this.player.getName().getString());
                     }

                     if (this.player.noPhysics || this.player.isSleeping() || (!flag2 || !serverlevel.noCollision(this.player, aabb)) && !this.isPlayerCollidingWithAnythingNew(serverlevel, aabb, d0, d1, d2)) {
                        // CraftBukkit start - fire PlayerMoveEvent
                        // Rest to old location first
                        this.player.absMoveTo(prevX, prevY, prevZ, prevYaw, prevPitch);

                        org.bukkit.entity.Player player = this.getCraftPlayer();
                        Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                        Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                        // If the packet contains movement information then we update the To location with the correct XYZ.
                        if (p_9874_.hasPos) {
                           to.setX(p_9874_.x);
                           to.setY(p_9874_.y);
                           to.setZ(p_9874_.z);
                        }

                        // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                        if (p_9874_.hasRot) {
                           to.setYaw(p_9874_.yRot);
                           to.setPitch(p_9874_.xRot);
                        }

                        // Prevent 40 event-calls for less than a single pixel of movement >.>
                        double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                        float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                        if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                           this.lastPosX = to.getX();
                           this.lastPosY = to.getY();
                           this.lastPosZ = to.getZ();
                           this.lastYaw = to.getYaw();
                           this.lastPitch = to.getPitch();

                           // Skip the first time we do this
                           if (from.getX() != Double.MAX_VALUE) {
                              Location oldTo = to.clone();
                              PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                              this.cserver.getPluginManager().callEvent(event);

                              // If the event is cancelled we move the player back to their old location.
                              if (event.isCancelled()) {
                                 teleport(from);
                                 return;
                              }

                              // If a Plugin has changed the To destination then we teleport the Player
                              // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                              // We only do this if the Event was not cancelled.
                              if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                                 this.player.getBukkitEntity().teleport(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                                 return;
                              }

                              // Check to see if the Players Location has some how changed during the call of the event.
                              // This can happen due to a plugin teleporting the player instead of using .setTo()
                              if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                                 this.justTeleported = false;
                                 return;
                              }
                           }
                        }
                        this.player.absMoveTo(d0, d1, d2, f, f1); // Copied from above
                        // CraftBukkit end
                        this.clientIsFloating = d7 >= -0.03125D && !flag1 && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && !this.server.isFlightAllowed() && !this.player.getAbilities().mayfly && !this.player.hasEffect(MobEffects.LEVITATION) && !this.player.isFallFlying() && !this.player.isAutoSpinAttack() && this.noBlocksAround(this.player);
                        this.player.serverLevel().getChunkSource().move(this.player);
                        this.player.doCheckFallDamage(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5, p_9874_.isOnGround());
                        this.player.setOnGroundWithKnownMovement(p_9874_.isOnGround(), new Vec3(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5));
                        if (flag) {
                           this.player.resetFallDistance();
                        }

                        this.player.checkMovementStatistics(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);
                        this.lastGoodX = this.player.getX();
                        this.lastGoodY = this.player.getY();
                        this.lastGoodZ = this.player.getZ();
                     } else {
                        this.internalTeleport(d3, d4, d5, f, f1, Collections.emptySet()); // CraftBukkit - SPIGOT-1807: Don't call teleport event, when the client thinks the player is falling, because the chunks are not loaded on the client yet.
                        this.player.doCheckFallDamage(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5, p_9874_.isOnGround());
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isPlayerCollidingWithAnythingNew(LevelReader p_289008_, AABB p_288986_, double p_288990_, double p_288991_, double p_288967_) {
      AABB aabb = this.player.getBoundingBox().move(p_288990_ - this.player.getX(), p_288991_ - this.player.getY(), p_288967_ - this.player.getZ());
      Iterable<VoxelShape> iterable = p_289008_.getCollisions(this.player, aabb.deflate((double)1.0E-5F));
      VoxelShape voxelshape = Shapes.create(p_288986_.deflate((double)1.0E-5F));

      for(VoxelShape voxelshape1 : iterable) {
         if (!Shapes.joinIsNotEmpty(voxelshape1, voxelshape, BooleanOp.AND)) {
            return true;
         }
      }

      return false;
   }

   // CraftBukkit start - Delegate to teleport(Location)
   public void teleport(double p_9775_, double p_9776_, double p_9777_, float p_9778_, float p_9779_) {
      this.teleport(p_9775_, p_9776_, p_9777_, p_9778_, p_9779_, PlayerTeleportEvent.TeleportCause.UNKNOWN);
   }

   public void teleport(double d0, double d1, double d2, float f, float f1, PlayerTeleportEvent.TeleportCause cause) {
      this.teleport(d0, d1, d2, f, f1, Collections.emptySet(), cause);
   }

   private final AtomicReference<PlayerTeleportEvent.TeleportCause> teleport$cause = new AtomicReference<>(PlayerTeleportEvent.TeleportCause.UNKNOWN);
   private final AtomicBoolean teleport$boolean = new AtomicBoolean(false);
   public void teleport$cause(PlayerTeleportEvent.TeleportCause cause) {
      this.teleport$cause.set(cause);
   }

   public void teleport(double p_9781_, double p_9782_, double p_9783_, float p_9784_, float p_9785_, Set<RelativeMovement> p_9786_) {
      org.bukkit.entity.Player player = this.getCraftPlayer();
      Location from = player.getLocation();

      double x = p_9781_;
      double y = p_9782_;
      double z = p_9783_;
      float yaw = p_9784_;
      float pitch = p_9785_;

      Location to = new Location(this.getCraftPlayer().getWorld(), x, y, z, yaw, pitch);
      // SPIGOT-5171: Triggered on join
      if (from.equals(to)) {
         this.internalTeleport(p_9781_, p_9782_, p_9783_, p_9784_, p_9785_, p_9786_);
         teleport$boolean.set(false);
         return; // CraftBukkit - Return event status
      }

      if (!AsyncCatcher.catchAsync()) {
         PlayerTeleportEvent event = new PlayerTeleportEvent(player, from.clone(), to.clone(), teleport$cause.getAndSet(PlayerTeleportEvent.TeleportCause.UNKNOWN));
         this.cserver.getPluginManager().callEvent(event);

         if (event.isCancelled() || !to.equals(event.getTo())) {
            p_9786_ = Collections.emptySet(); // Can't relative teleport
            to = event.isCancelled() ? event.getFrom() : event.getTo();
            p_9781_ = to.getX();
            p_9782_ = to.getY();
            p_9783_ = to.getZ();
            p_9784_ = to.getYaw();
            p_9785_ = to.getPitch();
         }

         this.internalTeleport(p_9781_, p_9782_, p_9783_, p_9784_, p_9785_, p_9786_);
         if (event.isCancelled())  teleport$boolean.set(true);
      } else {
         this.internalTeleport(p_9781_, p_9782_, p_9783_, p_9784_, p_9785_, p_9786_);
         teleport$boolean.set(false);
      }
   }

   public boolean teleport(double d0, double d1, double d2, float f, float f1, Set<RelativeMovement> set, PlayerTeleportEvent.TeleportCause cause) { // CraftBukkit - Return event status
      this.teleport$cause(cause);
      teleport(d0, d1, d2, f, f1, set);
      return teleport$boolean.getAndSet(false);
   }

   public void teleport(Location dest) {
      internalTeleport(dest.getX(), dest.getY(), dest.getZ(), dest.getYaw(), dest.getPitch(), Collections.emptySet());
   }

   private void internalTeleport(double pX, double pY, double pZ, float pYaw, float pPitch, Set<RelativeMovement> pRelativeSet) {
      // CraftBukkit start
      if (Float.isNaN(pYaw)) {
         pYaw = 0;
      }
      if (Float.isNaN(pPitch)) {
         pPitch = 0;
      }

      this.justTeleported = true;
      // CraftBukkit end
      double d0 = pRelativeSet.contains(RelativeMovement.X) ? this.player.getX() : 0.0D;
      double d1 = pRelativeSet.contains(RelativeMovement.Y) ? this.player.getY() : 0.0D;
      double d2 = pRelativeSet.contains(RelativeMovement.Z) ? this.player.getZ() : 0.0D;
      float f = pRelativeSet.contains(RelativeMovement.Y_ROT) ? this.player.getYRot() : 0.0F;
      float f1 = pRelativeSet.contains(RelativeMovement.X_ROT) ? this.player.getXRot() : 0.0F;
      this.awaitingPositionFromClient = new Vec3(pX, pY, pZ);
      if (++this.awaitingTeleport == Integer.MAX_VALUE) {
         this.awaitingTeleport = 0;
      }

      // CraftBukkit start - update last location
      this.lastPosX = this.awaitingPositionFromClient.x;
      this.lastPosY = this.awaitingPositionFromClient.y;
      this.lastPosZ = this.awaitingPositionFromClient.z;
      this.lastYaw = f;
      this.lastPitch = f1;
      // CraftBukkit end

      this.awaitingTeleportTime = this.tickCount;
      this.player.absMoveTo(pX, pY, pZ, pYaw, pPitch);
      this.player.connection.send(new ClientboundPlayerPositionPacket(pX - d0, pY - d1, pZ - d2, pYaw - f, pPitch - f1, pRelativeSet, this.awaitingTeleport));
   }

   public void handlePlayerAction(ServerboundPlayerActionPacket p_9889_) {
      PacketUtils.ensureRunningOnSameThread(p_9889_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      BlockPos blockpos = p_9889_.getPos();
      this.player.resetLastActionTime();
      ServerboundPlayerActionPacket.Action serverboundplayeractionpacket$action = p_9889_.getAction();
      switch (serverboundplayeractionpacket$action) {
         case SWAP_ITEM_WITH_OFFHAND:
            if (!this.player.isSpectator()) {
               ItemStack itemstack = this.player.getItemInHand(InteractionHand.OFF_HAND);
               var event = net.minecraftforge.common.ForgeHooks.onLivingSwapHandItems(this.player);
               if (event.isCanceled()) return;

               // CraftBukkit start - inspiration taken from DispenserRegistry (See SpigotCraft#394)
               CraftItemStack mainHand = CraftItemStack.asCraftMirror(event.getItemSwappedToMainHand());
               CraftItemStack offHand = CraftItemStack.asCraftMirror(event.getItemSwappedToOffHand());
               PlayerSwapHandItemsEvent swapItemsEvent = new PlayerSwapHandItemsEvent(getCraftPlayer(), mainHand.clone(), offHand.clone());
               this.cserver.getPluginManager().callEvent(swapItemsEvent);
               if (swapItemsEvent.isCancelled()) {
                  return;
               }
               if (swapItemsEvent.getOffHandItem().equals(offHand)) {
                  this.player.setItemInHand(InteractionHand.OFF_HAND, event.getItemSwappedToOffHand());
               } else {
                  this.player.setItemInHand(InteractionHand.OFF_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getOffHandItem()));
               }
               if (swapItemsEvent.getMainHandItem().equals(mainHand)) {
                  this.player.setItemInHand(InteractionHand.MAIN_HAND, event.getItemSwappedToMainHand());
               } else {
                  this.player.setItemInHand(InteractionHand.MAIN_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getMainHandItem()));
               }
               // CraftBukkit end
               this.player.stopUsingItem();
            }

            return;
         case DROP_ITEM:
            if (!this.player.isSpectator()) {
               this.player.drop(false);
            }

            return;
         case DROP_ALL_ITEMS:
            if (!this.player.isSpectator()) {
               // limit how quickly items can be dropped
               // If the ticks aren't the same then the count starts from 0 and we update the lastDropTick.
               if (this.lastDropTick != MinecraftServer.currentTick) {
                  this.dropCount = 0;
                  this.lastDropTick = MinecraftServer.currentTick;
               } else {
                  // Else we increment the drop count and check the amount.
                  this.dropCount++;
                  if (this.dropCount >= 20) {
                     LOGGER.warn(this.player.getScoreboardName() + " dropped their items too quickly!");
                     this.disconnect("You dropped your items too quickly (Hacking?)");
                     return;
                  }
               }
               // CraftBukkit end
               this.player.drop(true);
            }

            return;
         case RELEASE_USE_ITEM:
            this.player.releaseUsingItem();
            return;
         case START_DESTROY_BLOCK:
         case ABORT_DESTROY_BLOCK:
         case STOP_DESTROY_BLOCK:
            this.player.gameMode.handleBlockBreakAction(blockpos, serverboundplayeractionpacket$action, p_9889_.getDirection(), this.player.level().getMaxBuildHeight(), p_9889_.getSequence());
            this.player.connection.ackBlockChangesUpTo(p_9889_.getSequence());
            return;
         default:
            throw new IllegalArgumentException("Invalid player action");
      }
   }

   private static boolean wasBlockPlacementAttempt(ServerPlayer p_9791_, ItemStack p_9792_) {
      if (p_9792_.isEmpty()) {
         return false;
      } else {
         Item item = p_9792_.getItem();
         return (item instanceof BlockItem || item instanceof BucketItem) && !p_9791_.getCooldowns().isOnCooldown(item);
      }
   }

   // Spigot start - limit place/interactions
   private int limitedPackets;
   private long lastLimitedPacket = -1;

   private boolean checkLimit(long timestamp) {
      if (this.lastLimitedPacket != -1 && timestamp - this.lastLimitedPacket < 30 && this.limitedPackets++ >= 4) {
         return false;
      }

      if (this.lastLimitedPacket == -1 || timestamp - this.lastLimitedPacket >= 30) {
         this.lastLimitedPacket = timestamp;
         this.limitedPackets = 0;
         return true;
      }

      return true;
   }
   // Spigot end

   public void handleUseItemOn(ServerboundUseItemOnPacket p_9930_) {
      PacketUtils.ensureRunningOnSameThread(p_9930_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      if (!checkLimit(p_9930_.timestamp)) return; // Spigot - check limit
      this.player.connection.ackBlockChangesUpTo(p_9930_.getSequence());
      ServerLevel serverlevel = this.player.serverLevel();
      InteractionHand interactionhand = p_9930_.getHand();
      ItemStack itemstack = this.player.getItemInHand(interactionhand);
      if (itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
         BlockHitResult blockhitresult = p_9930_.getHitResult();
         Vec3 vec3 = blockhitresult.getLocation();
         BlockPos blockpos = blockhitresult.getBlockPos();
         Vec3 vec31 = Vec3.atCenterOf(blockpos);
          boolean canReachUnmodded = this.testPlayerCanReach(blockpos);
          boolean canReachModded = this.player.canReach(blockpos, 1.5); // Vanilla uses eye-to-center distance < ServerGamePacketListenerImpl which is 6*6, default block reach is 4.5, so add 1.5 padding
          if (canReachUnmodded == canReachModded ? this.player.canReachRaw(blockpos, 1.5) : canReachModded) {
            Vec3 vec32 = vec3.subtract(vec31);
            double d0 = 1.0000001D;
            if (Math.abs(vec32.x()) < 1.0000001D && Math.abs(vec32.y()) < 1.0000001D && Math.abs(vec32.z()) < 1.0000001D) {
               Direction direction = blockhitresult.getDirection();
               this.player.resetLastActionTime();
               int i = this.player.level().getMaxBuildHeight();
               if (blockpos.getY() < i) {
                  if (this.awaitingPositionFromClient == null && serverlevel.mayInteract(this.player, blockpos)) {
                     this.player.stopUsingItem(); // CraftBukkit - SPIGOT-4706
                     InteractionResult interactionresult = this.player.gameMode.useItemOn(this.player, serverlevel, itemstack, interactionhand, blockhitresult);
                     if (direction == Direction.UP && !interactionresult.consumesAction() && blockpos.getY() >= i - 1 && wasBlockPlacementAttempt(this.player, itemstack)) {
                        Component component = Component.translatable("build.tooHigh", i - 1).withStyle(ChatFormatting.RED);
                        this.player.sendSystemMessage(component, true);
                     } else if (interactionresult.shouldSwing()) {
                        this.player.swing(interactionhand, true);
                     }
                  }
               } else {
                  Component component1 = Component.translatable("build.tooHigh", i - 1).withStyle(ChatFormatting.RED);
                  this.player.sendSystemMessage(component1, true);
               }

               this.player.connection.send(new ClientboundBlockUpdatePacket(serverlevel, blockpos));
               this.player.connection.send(new ClientboundBlockUpdatePacket(serverlevel, blockpos.relative(direction)));
            } else {
               LOGGER.warn("Rejecting UseItemOnPacket from {}: Location {} too far away from hit block {}.", this.player.getGameProfile().getName(), vec3, blockpos);
            }
         }
      }
   }

    private boolean testPlayerCanReach(BlockPos pos) {
       return this.player.canReach(pos, 1.5);
   }

   public void handleUseItem(ServerboundUseItemPacket p_9932_) {
      PacketUtils.ensureRunningOnSameThread(p_9932_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      if (!checkLimit(p_9932_.timestamp)) return; // Spigot - check limit
      InteractionHand interactionhand = p_9932_.getHand();
      ItemStack itemstack = this.player.getItemInHand(interactionhand);
      if(BanItem.check(this.player, itemstack)) return; // Mohist - banitem
      this.ackBlockChangesUpTo(p_9932_.getSequence());
      ServerLevel serverlevel = this.player.serverLevel();
      this.player.resetLastActionTime();
      if (!itemstack.isEmpty() && itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
         // CraftBukkit start
         // Raytrace to look for 'rogue armswings'
         float f1 = this.player.getXRot();
         float f2 = this.player.getYRot();
         double d0 = this.player.getX();
         double d1 = this.player.getY() + (double) this.player.getEyeHeight();
         double d2 = this.player.getZ();
         Vec3 vec3d = new Vec3(d0, d1, d2);

         float f3 = Mth.cos(-f2 * 0.017453292F - 3.1415927F);
         float f4 = Mth.sin(-f2 * 0.017453292F - 3.1415927F);
         float f5 = -Mth.cos(-f1 * 0.017453292F);
         float f6 = Mth.sin(-f1 * 0.017453292F);
         float f7 = f4 * f5;
         float f8 = f3 * f5;
         double d3 = player.gameMode.getGameModeForPlayer()== GameType.CREATIVE ? 5.0D : 4.5D;
         Vec3 vec3d1 = vec3d.add((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
         HitResult movingobjectposition = this.player.level.clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

         boolean cancelled;
         if (movingobjectposition == null || movingobjectposition.getType() != HitResult.Type.BLOCK) {
            org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_AIR, itemstack, interactionhand);
            cancelled = event.useItemInHand() == Event.Result.DENY;
         } else {
            BlockHitResult movingobjectpositionblock = (BlockHitResult) movingobjectposition;
            if (player.gameMode.firedInteract && player.gameMode.interactPosition.equals(movingobjectpositionblock.getBlockPos()) && player.gameMode.interactHand == interactionhand && ItemStack.isSameItemSameTags(player.gameMode.interactItemStack, itemstack)) {
               cancelled = player.gameMode.interactResult;
            } else {
               org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, movingobjectpositionblock.getBlockPos(), movingobjectpositionblock.getDirection(), itemstack, true, interactionhand, movingobjectpositionblock.getLocation());
               cancelled = event.useItemInHand() == Event.Result.DENY;
            }
            player.gameMode.firedInteract = false;
         }

         if (cancelled) {
            this.player.getBukkitEntity().updateInventory(); // SPIGOT-2524
            return;
         }
         itemstack = this.player.getItemInHand(interactionhand); // Update in case it was changed in the event
         if (itemstack.isEmpty()) {
            return;
         }
         // CraftBukkit end
         InteractionResult interactionresult = this.player.gameMode.useItem(this.player, serverlevel, itemstack, interactionhand);
         if (interactionresult.shouldSwing()) {
            this.player.swing(interactionhand, true);
         }

      }
   }

   public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket p_9928_) {
      PacketUtils.ensureRunningOnSameThread(p_9928_, this, this.player.serverLevel());
      if (this.player.isSpectator()) {
         for(ServerLevel serverlevel : this.server.getAllLevels()) {
            Entity entity = p_9928_.getEntity(serverlevel);
            if (entity != null) {
               this.player.teleportTo(serverlevel, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE); // CraftBukkit
               return;
            }
         }
      }

   }

   public void handleResourcePackResponse(ServerboundResourcePackPacket p_9901_) {
      PacketUtils.ensureRunningOnSameThread(p_9901_, this, this.player.serverLevel());
      if (p_9901_.getAction() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
         LOGGER.info("Disconnecting {} due to resource pack rejection", (Object)this.player.getName());
         this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"));
      }
      this.cserver.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(getCraftPlayer(), PlayerResourcePackStatusEvent.Status.values()[p_9901_.action.ordinal()])); // CraftBukkit
   }

   public void handlePaddleBoat(ServerboundPaddleBoatPacket p_9878_) {
      PacketUtils.ensureRunningOnSameThread(p_9878_, this, this.player.serverLevel());
      Entity entity = this.player.getControlledVehicle();
      if (entity instanceof Boat boat) {
         boat.setPaddleState(p_9878_.getLeft(), p_9878_.getRight());
      }

   }

   public void handlePong(ServerboundPongPacket p_143652_) {
   }

   public void onDisconnect(Component p_9825_) {
      // CraftBukkit start - Rarely it would send a disconnect line twice
      if (this.processedDisconnect) {
         return;
      } else {
         this.processedDisconnect = true;
      }
      // CraftBukkit end
      this.chatMessageChain.close();
      LOGGER.info("{} lost connection: {}", this.player.getName().getString(), p_9825_.getString());
      this.player.disconnect();
      this.server.getPlayerList().remove(this.player);
      String quitMessage = this.server.getPlayerList().quitMessage;
      if ((quitMessage != null) && (quitMessage.length() > 0)) {
         this.server.getPlayerList().broadcastMessage(CraftChatMessage.fromString(quitMessage));
      }
      // CraftBukkit end
      this.player.getTextFilter().leave();
      if (this.isSingleplayerOwner()) {
         LOGGER.info("Stopping singleplayer server as player logged out");
         this.server.halt(false);
      }

   }

   public void ackBlockChangesUpTo(int p_215202_) {
      if (p_215202_ < 0) {
         throw new IllegalArgumentException("Expected packet sequence nr >= 0");
      } else {
         this.ackBlockChangesUpTo = Math.max(p_215202_, this.ackBlockChangesUpTo);
      }
   }

   public void send(Packet<?> p_9830_) {
      this.send(p_9830_, (PacketSendListener)null);
   }

   public void send(Packet<?> p_243227_, @Nullable PacketSendListener p_243273_) {
      // CraftBukkit start
      if (p_243227_ == null || this.processedDisconnect) { // Spigot
         return;
      } else if (p_243227_ instanceof ClientboundSetDefaultSpawnPositionPacket) {
         ClientboundSetDefaultSpawnPositionPacket packet6 = (ClientboundSetDefaultSpawnPositionPacket) p_243227_;
         this.player.compassTarget = CraftLocation.toBukkit(packet6.pos, this.getCraftPlayer().getWorld());
      }
      // CraftBukkit end
      try {
         this.connection.send(p_243227_, p_243273_);
      } catch (Throwable throwable) {
         CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
         CrashReportCategory crashreportcategory = crashreport.addCategory("Packet being sent");
         crashreportcategory.setDetail("Packet class", () -> {
            return p_243227_.getClass().getCanonicalName();
         });
         throw new ReportedException(crashreport);
      }
   }

   public void handleSetCarriedItem(ServerboundSetCarriedItemPacket p_9909_) {
      PacketUtils.ensureRunningOnSameThread(p_9909_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      if (p_9909_.getSlot() >= 0 && p_9909_.getSlot() < Inventory.getSelectionSize()) {
         PlayerItemHeldEvent event = new PlayerItemHeldEvent(this.getCraftPlayer(), this.player.getInventory().selected, p_9909_.getSlot());
         this.cserver.getPluginManager().callEvent(event);
         if (event.isCancelled()) {
            this.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
            this.player.resetLastActionTime();
            return;
         }
         // CraftBukkit end
         if (this.player.getInventory().selected != p_9909_.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            this.player.stopUsingItem();
         }

         this.player.getInventory().selected = p_9909_.getSlot();
         this.player.resetLastActionTime();
      } else {
         LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
         this.disconnect("Invalid hotbar selection (Hacking?)"); // CraftBukkit
      }
   }

   public void handleChat(ServerboundChatPacket p_9841_) {
      // CraftBukkit start - async chat
      // SPIGOT-3638
      if (this.server.isStopped()) {
         return;
      }
      // CraftBukkit end
      if (isChatMessageIllegal(p_9841_.message())) {
         this.disconnect(Component.translatable("multiplayer.disconnect.illegal_characters"));
      } else {
         Optional<LastSeenMessages> optional = this.tryHandleChat(p_9841_.message(), p_9841_.timeStamp(), p_9841_.lastSeenMessages());
         if (optional.isPresent()) {
            this.server.submit(() -> {
               PlayerChatMessage playerchatmessage;
               try {
                  playerchatmessage = this.getSignedMessage(p_9841_, optional.get());
               } catch (SignedMessageChain.DecodeException signedmessagechain$decodeexception) {
                  this.handleMessageDecodeFailure(signedmessagechain$decodeexception);
                  return;
               }

               CompletableFuture<FilteredText> completablefuture = this.filterTextPacket(playerchatmessage.signedContent());
               CompletableFuture<Component> completablefuture1 = net.minecraftforge.common.ForgeHooks.getServerChatSubmittedDecorator().decorate(this.player, playerchatmessage.decoratedContent());
               this.chatMessageChain.append((p_248212_) -> {
                  return CompletableFuture.allOf(completablefuture, completablefuture1).thenAcceptAsync((p_248218_) -> {
                     Component decoratedContent = completablefuture1.join();
                     if (decoratedContent == null)
                        return; // Forge: ServerChatEvent was canceled if this is null.
                     PlayerChatMessage playerchatmessage1 = playerchatmessage.withUnsignedContent(decoratedContent).filter(completablefuture.join().mask());
                     this.broadcastChatMessage(playerchatmessage1);
                  }, p_248212_);
               });
            });
         }

      }
   }

   public void handleChatCommand(ServerboundChatCommandPacket p_215225_) {
      if (isChatMessageIllegal(p_215225_.command())) {
         this.disconnect(Component.translatable("multiplayer.disconnect.illegal_characters"));
      } else {
         Optional<LastSeenMessages> optional = this.tryHandleChat(p_215225_.command(), p_215225_.timeStamp(), p_215225_.lastSeenMessages());
         if (optional.isPresent()) {
            this.server.submit(() -> {
               // CraftBukkit start - SPIGOT-7346: Prevent disconnected players from executing commands
               if (player.hasDisconnected()) {
                  return;
               }
               // CraftBukkit end

               this.performChatCommand(p_215225_, optional.get());
               this.detectRateSpam("/" + p_215225_.command()); // Spigot
            });
         }

      }
   }

   private void performChatCommand(ServerboundChatCommandPacket p_251139_, LastSeenMessages p_250484_) {
      // CraftBukkit start
      String command = "/" + p_251139_.command();
      LOGGER.info(this.player.getScoreboardName() + " issued server command: " + command);

      PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(getCraftPlayer(), command, new LazyPlayerSet(server));
      this.cserver.getPluginManager().callEvent(event);

      if (event.isCancelled()) {
         return;
      }
      command = event.getMessage().substring(1);

      ParseResults<CommandSourceStack> parseresults = this.parseCommand(command);
      // CraftBukkit end

      Map<String, PlayerChatMessage> map;
      try {
         map = (p_251139_.command().equals(command)) ? this.collectSignedArguments(p_251139_, SignableCommand.of(parseresults), p_250484_) : Collections.emptyMap(); // CraftBukkit
      } catch (SignedMessageChain.DecodeException signedmessagechain$decodeexception) {
         this.handleMessageDecodeFailure(signedmessagechain$decodeexception);
         return;
      }

      CommandSigningContext commandsigningcontext = new CommandSigningContext.SignedArguments(map);
      parseresults = Commands.<CommandSourceStack>mapSource(parseresults, (p_242749_) -> {
         return p_242749_.withSigningContext(commandsigningcontext);
      });
      this.server.getCommands().performCommand(parseresults, command); // CraftBukkit
   }

   private void handleMessageDecodeFailure(SignedMessageChain.DecodeException p_252068_) {
      if (p_252068_.shouldDisconnect()) {
         this.disconnect(p_252068_.getComponent());
      } else {
         this.player.sendSystemMessage(p_252068_.getComponent().copy().withStyle(ChatFormatting.RED));
      }

   }

   private Map<String, PlayerChatMessage> collectSignedArguments(ServerboundChatCommandPacket p_249441_, SignableCommand<?> p_250039_, LastSeenMessages p_249207_) throws SignedMessageChain.DecodeException {
      Map<String, PlayerChatMessage> map = new Object2ObjectOpenHashMap<>();

      for(SignableCommand.Argument<?> argument : p_250039_.arguments()) {
         MessageSignature messagesignature = p_249441_.argumentSignatures().get(argument.name());
         SignedMessageBody signedmessagebody = new SignedMessageBody(argument.value(), p_249441_.timeStamp(), p_249441_.salt(), p_249207_);
         map.put(argument.name(), this.signedMessageDecoder.unpack(messagesignature, signedmessagebody));
      }

      return map;
   }

   private ParseResults<CommandSourceStack> parseCommand(String p_242938_) {
      CommandDispatcher<CommandSourceStack> commanddispatcher = this.server.getCommands().getDispatcher();
      return commanddispatcher.parse(p_242938_, this.player.createCommandSourceStack());
   }

   private Optional<LastSeenMessages> tryHandleChat(String p_251364_, Instant p_248959_, LastSeenMessages.Update p_249613_) {
      if (!this.updateChatOrder(p_248959_)) {
         LOGGER.warn("{} sent out-of-order chat: '{}'", this.player.getName().getString(), p_251364_);
         this.disconnect(Component.translatable("multiplayer.disconnect.out_of_order_chat"));
         return Optional.empty();
      } else {
         Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(p_249613_);
         if (this.player.isRemoved() || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) { // CraftBukkit - dead men tell no tales
            this.send(new ClientboundSystemChatPacket(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED), false));
            return Optional.empty();
         } else {
            this.player.resetLastActionTime();
            return optional;
         }
      }
   }

   private Optional<LastSeenMessages> unpackAndApplyLastSeen(LastSeenMessages.Update p_249673_) {
      synchronized(this.lastSeenMessages) {
         Optional<LastSeenMessages> optional = this.lastSeenMessages.applyUpdate(p_249673_);
         if (optional.isEmpty()) {
            LOGGER.warn("Failed to validate message acknowledgements from {}", (Object)this.player.getName().getString());
            this.disconnect(CHAT_VALIDATION_FAILED);
         }

         return optional;
      }
   }

   private boolean updateChatOrder(Instant p_215237_) {
      Instant instant;
      do {
         instant = this.lastChatTimeStamp.get();
         if (p_215237_.isBefore(instant)) {
            return false;
         }
      } while(!this.lastChatTimeStamp.compareAndSet(instant, p_215237_));

      return true;
   }

   private static boolean isChatMessageIllegal(String p_215215_) {
      for(int i = 0; i < p_215215_.length(); ++i) {
         if (!SharedConstants.isAllowedChatCharacter(p_215215_.charAt(i))) {
            return true;
         }
      }

      return false;
   }

   private PlayerChatMessage getSignedMessage(ServerboundChatPacket p_251061_, LastSeenMessages p_250566_) throws SignedMessageChain.DecodeException {
      SignedMessageBody signedmessagebody = new SignedMessageBody(p_251061_.message(), p_251061_.timeStamp(), p_251061_.salt(), p_250566_);
      return this.signedMessageDecoder.unpack(p_251061_.signature(), signedmessagebody);
   }

   private void broadcastChatMessage(PlayerChatMessage p_243277_) {
      // CraftBukkit start
      String s = p_243277_.signedContent();
      if (s.isEmpty()) {
         LOGGER.warn(this.player.getScoreboardName() + " tried to send an empty message");
      } else if (getCraftPlayer().isConversing()) {
         final String conversationInput = s;
         this.server.processQueue.add(new Runnable() {
             @Override
             public void run() {
                 ServerGamePacketListenerImpl.this.getCraftPlayer().acceptConversationInput(conversationInput);
             }
         });
      } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) { // Re-add "Command Only" flag check
         this.send(new ClientboundSystemChatPacket(Component.translatable("chat.cannotSend").withStyle(ChatFormatting.RED), false));
      } else {
         ChatPatchFix.chat(this, s, p_243277_, true);
      }
      // CraftBukkit end
      this.detectRateSpam(s); // Spigot
   }

   private void detectRateSpam(String s) {
      boolean counted = true;
      for ( String exclude : org.spigotmc.SpigotConfig.spamExclusions )
      {
         if ( exclude != null && s.startsWith( exclude ) )
         {
            counted = false;
            break;
         }
      }
      // Spigot end
      // this.chatSpamTickCount += 20;
      if (counted && this.chatSpamTickCount.addAndGet(20) > 200 && !this.server.getPlayerList().isOp(this.player.getGameProfile())) {
         // CraftBukkit end
         this.disconnect(Component.translatable("disconnect.spam"));
      }

   }

   public void handleChatAck(ServerboundChatAckPacket p_242387_) {
      synchronized(this.lastSeenMessages) {
         if (!this.lastSeenMessages.applyOffset(p_242387_.offset())) {
            LOGGER.warn("Failed to validate message acknowledgements from {}", (Object)this.player.getName().getString());
            this.disconnect(CHAT_VALIDATION_FAILED);
         }

      }
   }

   public void handleAnimate(ServerboundSwingPacket p_9926_) {
      PacketUtils.ensureRunningOnSameThread(p_9926_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      this.player.resetLastActionTime();
      // CraftBukkit start - Raytrace to look for 'rogue armswings'
      float f1 = this.player.getXRot();
      float f2 = this.player.getYRot();
      double d0 = this.player.getX();
      double d1 = this.player.getY() + (double) this.player.getEyeHeight();
      double d2 = this.player.getZ();
      Location origin = new Location(this.player.level.getWorld(), d0, d1, d2, f2, f1);

      double d3 = player.gameMode.getGameModeForPlayer() == GameType.CREATIVE ? 5.0D : 4.5D;
      // SPIGOT-5607: Only call interact event if no block or entity is being clicked. Use bukkit ray trace method, because it handles blocks and entities at the same time
      // SPIGOT-7429: Make sure to call PlayerInteractEvent for spectators and non-pickable entities
      org.bukkit.util.RayTraceResult result = this.player.level().getWorld().rayTrace(origin, origin.getDirection(), d3, org.bukkit.FluidCollisionMode.NEVER, false, 0.1, new Predicate<org.bukkit.entity.Entity>() {
          @Override
          public boolean test(org.bukkit.entity.Entity entity) {
              Entity handle = ((CraftEntity) entity).getHandle();
              return entity != ServerGamePacketListenerImpl.this.player.getBukkitEntity() && ServerGamePacketListenerImpl.this.player.getBukkitEntity().canSee(entity) && !handle.isSpectator() && handle.isPickable() && !handle.isPassengerOfSameVehicle(player);
          }
      });
      if (result == null) {
         CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_AIR, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
      }

      // Arm swing animation
      PlayerAnimationEvent event = new PlayerAnimationEvent(this.getCraftPlayer(), (p_9926_.getHand() == InteractionHand.MAIN_HAND) ? PlayerAnimationType.ARM_SWING : PlayerAnimationType.OFF_ARM_SWING);
      this.cserver.getPluginManager().callEvent(event);

      if (event.isCancelled()) return;
      // CraftBukkit end
      this.player.swing(p_9926_.getHand());
   }

   public void handlePlayerCommand(ServerboundPlayerCommandPacket p_9891_) {
      PacketUtils.ensureRunningOnSameThread(p_9891_, this, this.player.serverLevel());
      // CraftBukkit start
      if (this.player.isRemoved()) return;
      switch (p_9891_.getAction()) {
         case PRESS_SHIFT_KEY:
         case RELEASE_SHIFT_KEY:
            PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getCraftPlayer(), p_9891_.getAction() == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY);
            this.cserver.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
               return;
            }
            break;
         case START_SPRINTING:
         case STOP_SPRINTING:
            PlayerToggleSprintEvent e2 = new PlayerToggleSprintEvent(this.getCraftPlayer(), p_9891_.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING);
            this.cserver.getPluginManager().callEvent(e2);

            if (e2.isCancelled()) {
               return;
            }
            break;
      }
      // CraftBukkit end
      this.player.resetLastActionTime();
      switch (p_9891_.getAction()) {
         case PRESS_SHIFT_KEY:
            this.player.setShiftKeyDown(true);
            break;
         case RELEASE_SHIFT_KEY:
            this.player.setShiftKeyDown(false);
            break;
         case START_SPRINTING:
            this.player.setSprinting(true);
            break;
         case STOP_SPRINTING:
            this.player.setSprinting(false);
            break;
         case STOP_SLEEPING:
            if (this.player.isSleeping()) {
               this.player.stopSleepInBed(false, true);
               this.awaitingPositionFromClient = this.player.position();
            }
            break;
         case START_RIDING_JUMP:
            Entity entity2 = this.player.getControlledVehicle();
            if (entity2 instanceof PlayerRideableJumping playerrideablejumping1) {
               int i = p_9891_.getData();
               if (playerrideablejumping1.canJump() && i > 0) {
                  playerrideablejumping1.handleStartJump(i);
               }
            }
            break;
         case STOP_RIDING_JUMP:
            Entity entity1 = this.player.getControlledVehicle();
            if (entity1 instanceof PlayerRideableJumping playerrideablejumping) {
               playerrideablejumping.handleStopJump();
            }
            break;
         case OPEN_INVENTORY:
            Entity $$2 = this.player.getVehicle();
            if ($$2 instanceof HasCustomInventoryScreen hascustominventoryscreen) {
               hascustominventoryscreen.openCustomInventoryScreen(this.player);
            }
            break;
         case START_FALL_FLYING:
            if (!this.player.tryToStartFallFlying()) {
               this.player.stopFallFlying();
            }
            break;
         default:
            throw new IllegalArgumentException("Invalid client command!");
      }

   }

   public void addPendingMessage(PlayerChatMessage p_242439_) {
      MessageSignature messagesignature = p_242439_.signature();
      if (messagesignature != null) {
         this.messageSignatureCache.push(p_242439_);
         int i;
         synchronized(this.lastSeenMessages) {
            this.lastSeenMessages.addPending(messagesignature);
            i = this.lastSeenMessages.trackedMessagesCount();
         }

         if (i > 4096) {
            this.disconnect(Component.translatable("multiplayer.disconnect.too_many_pending_chats"));
         }

      }
   }

   public void sendPlayerChatMessage(PlayerChatMessage p_250321_, ChatType.Bound p_250910_) {
      // CraftBukkit start - SPIGOT-7262: if hidden we have to send as disguised message. Query whether we should send at all (but changing this may not be expected).
      if (!getCraftPlayer().canSee(p_250321_.link().sender())) {
         sendDisguisedChatMessage(p_250321_.decoratedContent(), p_250910_);
         return;
      }
      // CraftBukkit end
      this.send(new ClientboundPlayerChatPacket(p_250321_.link().sender(), p_250321_.link().index(), p_250321_.signature(), p_250321_.signedBody().pack(this.messageSignatureCache), p_250321_.unsignedContent(), p_250321_.filterMask(), p_250910_.toNetwork(this.player.level().registryAccess())));
      this.addPendingMessage(p_250321_);
   }

   public void sendDisguisedChatMessage(Component p_251804_, ChatType.Bound p_250040_) {
      this.send(new ClientboundDisguisedChatPacket(p_251804_, p_250040_.toNetwork(this.player.level().registryAccess())));
   }

   public SocketAddress getRemoteAddress() {
      return this.connection.getRemoteAddress();
   }

   public void handleInteract(ServerboundInteractPacket p_9866_) {
      PacketUtils.ensureRunningOnSameThread(p_9866_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      if (BanItem.check(player)) return; // Mohist
      final ServerLevel serverlevel = this.player.serverLevel();
      final Entity entity = p_9866_.getTarget(serverlevel);
      this.player.resetLastActionTime();
      this.player.setShiftKeyDown(p_9866_.isUsingSecondaryAction());
      if (entity != null) {
         if (!serverlevel.getWorldBorder().isWithinBounds(entity.blockPosition())) {
            return;
         }

         AABB aabb = entity.getBoundingBox();
         if (this.player.canReachRaw(entity, 3.0)) { // Vanilla uses MAX_INTERACTION_DISTANCE which is 6*6 default entity reach is 3.0 so add 3.0 padding
            p_9866_.dispatch(new ServerboundInteractPacket.Handler() {
               PlayerInteractEntityEvent event;
               private void performInteraction(InteractionHand p_143679_, ServerGamePacketListenerImpl.EntityInteraction p_143680_) { // CraftBukkit
                  ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(p_143679_);
                  if (itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
                     ItemStack itemstack1 = itemstack.copy();
                     // CraftBukkit start
                     ItemStack itemInHand = ServerGamePacketListenerImpl.this.player.getItemInHand(p_143679_);
                     boolean triggerLeashUpdate = itemInHand != null && itemInHand.getItem() == Items.LEAD && entity instanceof Mob;
                     Item origItem = player.getInventory().getSelected() == null ? null : player.getInventory().getSelected().getItem();

                     cserver.getPluginManager().callEvent(event);

                     // Entity in bucket - SPIGOT-4048 and SPIGOT-6859a
                     if ((entity instanceof Bucketable && entity instanceof LivingEntity && origItem != null && origItem.asItem() == Items.WATER_BUCKET) && (event.isCancelled() || player.getInventory().getSelected() == null || player.getInventory().getSelected().getItem() != origItem)) {
                        send(new ClientboundAddEntityPacket(entity));
                        player.containerMenu.sendAllDataToRemote();
                     }

                     if (triggerLeashUpdate && (event.isCancelled() || player.getInventory().getSelected() == null || player.getInventory().getSelected().getItem() != origItem)) {
                        // Refresh the current leash state
                        send(new ClientboundSetEntityLinkPacket(entity, ((Mob) entity).getLeashHolder()));
                     }

                     if (event.isCancelled() || player.getInventory().getSelected() == null || player.getInventory().getSelected().getItem() != origItem) {
                        // Refresh the current entity metadata
                        entity.getEntityData().refresh(player);
                        // SPIGOT-7136 - Allays
                        if (entity instanceof Allay) {
                           send(new ClientboundSetEquipmentPacket(entity.getId(), Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values()).map(new Function<net.minecraft.world.entity.EquipmentSlot, Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>>() {
                               @Override
                               public Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack> apply(net.minecraft.world.entity.EquipmentSlot slot) {
                                   return Pair.of(slot, ((LivingEntity) entity).getItemBySlot(slot).copy());
                               }
                           }).collect(Collectors.toList())));
                           player.containerMenu.sendAllDataToRemote();
                        }
                     }

                     if (event.isCancelled()) {
                        return;
                     }
                     // CraftBukkit end
                     InteractionResult interactionresult = p_143680_.run(ServerGamePacketListenerImpl.this.player, entity, p_143679_);
                     // CraftBukkit start
                     if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                        player.containerMenu.sendAllDataToRemote();
                     }
                     // CraftBukkit end
                     if (interactionresult.consumesAction()) {
                        CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(ServerGamePacketListenerImpl.this.player, itemstack1, entity);
                        if (interactionresult.shouldSwing()) {
                           ServerGamePacketListenerImpl.this.player.swing(p_143679_, true);
                        }
                     }

                  }
               }

               public void onInteraction(InteractionHand p_143677_) {
                  event = new PlayerInteractEntityEvent(getCraftPlayer(), entity.getBukkitEntity(), (p_143677_ == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
                  this.performInteraction(p_143677_, Player::interactOn);
               }

               public void onInteraction(InteractionHand p_143682_, Vec3 p_143683_) {
                  event = new PlayerInteractAtEntityEvent(getCraftPlayer(), entity.getBukkitEntity(), new org.bukkit.util.Vector(p_143683_.x, p_143683_.y, p_143683_.z), (p_143682_ == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
                  this.performInteraction(p_143682_, (p_143686_, p_143687_, p_143688_) -> {
                     InteractionResult onInteractEntityAtResult = net.minecraftforge.common.ForgeHooks.onInteractEntityAt(player, entity, p_143683_, p_143682_);
                     if (onInteractEntityAtResult != null) return onInteractEntityAtResult;
                     return p_143687_.interactAt(p_143686_, p_143683_, p_143688_);
                  });
               }

               public void onAttack() {
                  // CraftBukkit
                  if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb) && !(entity instanceof AbstractArrow) && (entity != ServerGamePacketListenerImpl.this.player || player.isSpectator())) {
                     ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(InteractionHand.MAIN_HAND);
                     if (itemstack.isItemEnabled(serverlevel.enabledFeatures())) {
                        ServerGamePacketListenerImpl.this.player.attack(entity);
                        // CraftBukkit start
                        if (!itemstack.isEmpty() && itemstack.getCount() <= -1) {
                           player.containerMenu.sendAllDataToRemote();
                        }
                        // CraftBukkit end
                     }
                  } else {
                     ServerGamePacketListenerImpl.this.disconnect(Component.translatable("multiplayer.disconnect.invalid_entity_attacked"));
                     ServerGamePacketListenerImpl.LOGGER.warn("Player {} tried to attack an invalid entity", (Object)ServerGamePacketListenerImpl.this.player.getName().getString());
                  }
               }
            });
         }
      }
      // Paper start - PlayerUseUnknownEntityEvent
      else {
         p_9866_.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(net.minecraft.world.InteractionHand hand) {
               ServerGamePacketListenerImpl.this.callPlayerUseUnknownEntityEvent(p_9866_, hand, null);
            }

            @Override
            public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) {
               ServerGamePacketListenerImpl.this.callPlayerUseUnknownEntityEvent(p_9866_, hand, pos);
            }

            @Override
            public void onAttack() {
               ServerGamePacketListenerImpl.this.callPlayerUseUnknownEntityEvent(p_9866_, net.minecraft.world.InteractionHand.MAIN_HAND, null);
            }
         });
      }

   }


   private void callPlayerUseUnknownEntityEvent(ServerboundInteractPacket packet, InteractionHand hand, @Nullable net.minecraft.world.phys.Vec3 vector) {
      this.cserver.getPluginManager().callEvent(new PlayerUseUnknownEntityEvent(
              this.getCraftPlayer(),
              packet.getEntityId(),
              packet.isAttack(),
              hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND,
              vector != null ? new org.bukkit.util.Vector(vector.x, vector.y, vector.z) : null)
      );
   }
   // Paper end - PlayerUseUnknownEntityEvent

   public void handleClientCommand(ServerboundClientCommandPacket p_9843_) {
      PacketUtils.ensureRunningOnSameThread(p_9843_, this, this.player.serverLevel());
      this.player.resetLastActionTime();
      ServerboundClientCommandPacket.Action serverboundclientcommandpacket$action = p_9843_.getAction();
      switch (serverboundclientcommandpacket$action) {
         case PERFORM_RESPAWN:
            if (this.player.wonGame) {
               this.player.wonGame = false;
               this.server.getPlayerList().mohist$reason = PlayerRespawnEvent.RespawnReason.END_PORTAL; // Mohist
               this.player = this.server.getPlayerList().respawn(this.player, true);
               CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, Level.END, Level.OVERWORLD);
            } else {
               if (this.player.getHealth() > 0.0F) {
                  return;
               }

               this.server.getPlayerList().mohist$reason = PlayerRespawnEvent.RespawnReason.DEATH; // Mohist
               this.player = this.server.getPlayerList().respawn(this.player, false);
               if (this.server.isHardcore()) {
                  this.player.setGameMode(GameType.SPECTATOR);
                  this.player.level().getGameRules().getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(false, this.server);
               }
            }
            break;
         case REQUEST_STATS:
            this.player.getStats().sendStats(this.player);
      }

   }

   public void handleContainerClose(ServerboundContainerClosePacket p_9858_) {
      PacketUtils.ensureRunningOnSameThread(p_9858_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      this.player.doCloseContainer();
   }

   public void handleContainerClick(ServerboundContainerClickPacket p_9856_) {
      PacketUtils.ensureRunningOnSameThread(p_9856_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      this.player.resetLastActionTime();
      if (this.player.containerMenu.containerId == p_9856_.getContainerId() && this.player.containerMenu.stillValid(this.player)) { // CraftBukkit
         boolean cancelled = this.player.isSpectator(); // CraftBukkit - see below if
         if (false/*this.player.isSpectator()*/) { // CraftBukkit
            this.player.containerMenu.sendAllDataToRemote();
         } else if (!this.player.containerMenu.stillValid(this.player)) {
            LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
         } else {
            int i = p_9856_.getSlotNum();
            if (!this.player.containerMenu.isValidSlotIndex(i)) {
               LOGGER.debug("Player {} clicked invalid slot index: {}, available slots: {}", this.player.getName(), i, this.player.containerMenu.slots.size());
            } else {
               boolean flag = p_9856_.getStateId() != this.player.containerMenu.getStateId();
               this.player.containerMenu.suppressRemoteUpdates();

               // CraftBukkit start - Call InventoryClickEvent
               if (p_9856_.getSlotNum() < -1 && p_9856_.getSlotNum() != -999) {
                  return;
               }

               InventoryView inventoryView = this.player.containerMenu.getBukkitView();
               if(inventoryView == null)
               {
                  org.bukkit.inventory.Inventory inventory = new CraftInventory(new MohistModsInventory(this.player.containerMenu, this.player));
                  InventoryView newView = new CraftInventoryView(this.player.getBukkitEntity(), inventory, this.player.containerMenu);
                  inventoryView = newView;
                  this.player.containerMenu.bukkitView = newView;
               }
               InventoryType.SlotType type = inventoryView.getSlotType(p_9856_.getSlotNum());

               InventoryClickEvent event;
               ClickType click = ClickType.UNKNOWN;
               InventoryAction action = InventoryAction.UNKNOWN;

               ItemStack itemstack = ItemStack.EMPTY;

               switch (p_9856_.getClickType()) {
                  case PICKUP:
                     if (p_9856_.getButtonNum() == 0) {
                        click = ClickType.LEFT;
                     } else if (p_9856_.getButtonNum() == 1) {
                        click = ClickType.RIGHT;
                     }
                     if (p_9856_.getButtonNum() == 0 || p_9856_.getButtonNum() == 1) {
                        action = InventoryAction.NOTHING; // Don't want to repeat ourselves
                        if (p_9856_.getSlotNum() == -999) {
                           if (!player.containerMenu.getCarried().isEmpty()) {
                              action = p_9856_.getButtonNum() == 0 ? InventoryAction.DROP_ALL_CURSOR : InventoryAction.DROP_ONE_CURSOR;
                           }
                        } else if (p_9856_.getSlotNum() < 0) {
                           action = InventoryAction.NOTHING;
                        } else {
                           Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                           if (slot != null) {
                              ItemStack clickedItem = slot.getItem();
                              ItemStack cursor = player.containerMenu.getCarried();
                              if (clickedItem.isEmpty()) {
                                 if (!cursor.isEmpty()) {
                                    action = p_9856_.getButtonNum() == 0 ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_ONE;
                                 }
                              } else if (slot.mayPickup(player)) {
                                 if (cursor.isEmpty()) {
                                    action = p_9856_.getButtonNum() == 0 ? InventoryAction.PICKUP_ALL : InventoryAction.PICKUP_HALF;
                                 } else if (slot.mayPlace(cursor)) {
                                    if (ItemStack.isSameItemSameTags(clickedItem, cursor)) {
                                       int toPlace = p_9856_.getButtonNum() == 0 ? cursor.getCount() : 1;
                                       toPlace = Math.min(toPlace, clickedItem.getMaxStackSize() - clickedItem.getCount());
                                       toPlace = Math.min(toPlace, slot.container.getMaxStackSize() - clickedItem.getCount());
                                       if (toPlace == 1) {
                                          action = InventoryAction.PLACE_ONE;
                                       } else if (toPlace == cursor.getCount()) {
                                          action = InventoryAction.PLACE_ALL;
                                       } else if (toPlace < 0) {
                                          action = toPlace != -1 ? InventoryAction.PICKUP_SOME : InventoryAction.PICKUP_ONE; // this happens with oversized stacks
                                       } else if (toPlace != 0) {
                                          action = InventoryAction.PLACE_SOME;
                                       }
                                    } else if (cursor.getCount() <= slot.getMaxStackSize()) {
                                       action = InventoryAction.SWAP_WITH_CURSOR;
                                    }
                                 } else if (ItemStack.isSameItemSameTags(cursor, clickedItem)) {
                                    if (clickedItem.getCount() >= 0) {
                                       if (clickedItem.getCount() + cursor.getCount() <= cursor.getMaxStackSize()) {
                                          // As of 1.5, this is result slots only
                                          action = InventoryAction.PICKUP_ALL;
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                     break;
                  // TODO check on updates
                  case QUICK_MOVE:
                     if (p_9856_.getButtonNum() == 0) {
                        click = ClickType.SHIFT_LEFT;
                     } else if (p_9856_.getButtonNum() == 1) {
                        click = ClickType.SHIFT_RIGHT;
                     }
                     if (p_9856_.getButtonNum() == 0 || p_9856_.getButtonNum() == 1) {
                        if (p_9856_.getSlotNum() < 0) {
                           action = InventoryAction.NOTHING;
                        } else {
                           Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                           if (slot != null && slot.mayPickup(this.player) && slot.hasItem()) {
                              action = InventoryAction.MOVE_TO_OTHER_INVENTORY;
                           } else {
                              action = InventoryAction.NOTHING;
                           }
                        }
                     }
                     break;
                  case SWAP:
                     if ((p_9856_.getButtonNum() >= 0 && p_9856_.getButtonNum() < 9) || p_9856_.getButtonNum() == 40) {
                        click = (p_9856_.getButtonNum() == 40) ? ClickType.SWAP_OFFHAND : ClickType.NUMBER_KEY;
                        Slot clickedSlot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                        if (clickedSlot.mayPickup(player)) {
                           ItemStack hotbar = this.player.getInventory().getItem(p_9856_.getButtonNum());
                           boolean canCleanSwap = hotbar.isEmpty() || (clickedSlot.container == player.getInventory() && clickedSlot.mayPlace(hotbar)); // the slot will accept the hotbar item
                           if (clickedSlot.hasItem()) {
                              if (canCleanSwap) {
                                 action = InventoryAction.HOTBAR_SWAP;
                              } else {
                                 action = InventoryAction.HOTBAR_MOVE_AND_READD;
                              }
                           } else if (!clickedSlot.hasItem() && !hotbar.isEmpty() && clickedSlot.mayPlace(hotbar)) {
                              action = InventoryAction.HOTBAR_SWAP;
                           } else {
                              action = InventoryAction.NOTHING;
                           }
                        } else {
                           action = InventoryAction.NOTHING;
                        }
                     }
                     break;
                  case CLONE:
                     if (p_9856_.getButtonNum() == 2) {
                        click = ClickType.MIDDLE;
                        if (p_9856_.getSlotNum() < 0) {
                           action = InventoryAction.NOTHING;
                        } else {
                           Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                           if (slot != null && slot.hasItem() && player.getAbilities().instabuild && player.containerMenu.getCarried().isEmpty()) {
                              action = InventoryAction.CLONE_STACK;
                           } else {
                              action = InventoryAction.NOTHING;
                           }
                        }
                     } else {
                        click = ClickType.UNKNOWN;
                        action = InventoryAction.UNKNOWN;
                     }
                     break;
                  case THROW:
                     if (p_9856_.getSlotNum() >= 0) {
                        if (p_9856_.getButtonNum() == 0) {
                           click = ClickType.DROP;
                           Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                           if (slot != null && slot.hasItem() && slot.mayPickup(player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                              action = InventoryAction.DROP_ONE_SLOT;
                           } else {
                              action = InventoryAction.NOTHING;
                           }
                        } else if (p_9856_.getButtonNum() == 1) {
                           click = ClickType.CONTROL_DROP;
                           Slot slot = this.player.containerMenu.getSlot(p_9856_.getSlotNum());
                           if (slot != null && slot.hasItem() && slot.mayPickup(player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                              action = InventoryAction.DROP_ALL_SLOT;
                           } else {
                              action = InventoryAction.NOTHING;
                           }
                        }
                     } else {
                        // Sane default (because this happens when they are holding nothing. Don't ask why.)
                        click = ClickType.LEFT;
                        if (p_9856_.getButtonNum() == 1) {
                           click = ClickType.RIGHT;
                        }
                        action = InventoryAction.NOTHING;
                     }
                     break;
                  case QUICK_CRAFT:
                     this.player.containerMenu.clicked(p_9856_.getSlotNum(), p_9856_.getButtonNum(), p_9856_.getClickType(), this.player);
                     break;
                  case PICKUP_ALL:
                     click = ClickType.DOUBLE_CLICK;
                     action = InventoryAction.NOTHING;
                     if (p_9856_.getSlotNum() >= 0 && !this.player.containerMenu.getCarried().isEmpty()) {
                        ItemStack cursor = this.player.containerMenu.getCarried();
                        action = InventoryAction.NOTHING;
                        // Quick check for if we have any of the item
                        if (inventoryView.getTopInventory().contains(CraftMagicNumbers.getMaterial(cursor.getItem())) || inventoryView.getBottomInventory().contains(CraftMagicNumbers.getMaterial(cursor.getItem()))) {
                           action = InventoryAction.COLLECT_TO_CURSOR;
                        }
                     }
                     break;
                  default:
                     break;
               }

               if (p_9856_.getClickType() != net.minecraft.world.inventory.ClickType.QUICK_CRAFT) {
                  if (click == ClickType.NUMBER_KEY) {
                     event = new InventoryClickEvent(inventoryView, type, p_9856_.getSlotNum(), click, action, p_9856_.getButtonNum());
                  } else {
                     event = new InventoryClickEvent(inventoryView, type, p_9856_.getSlotNum(), click, action);
                  }

                  org.bukkit.inventory.Inventory top = inventoryView.getTopInventory();
                  if (p_9856_.getSlotNum() == 0 && top instanceof CraftingInventory) {
                     org.bukkit.inventory.Recipe recipe = ((CraftingInventory) top).getRecipe();
                     if (recipe != null) {
                        if (click == ClickType.NUMBER_KEY) {
                           event = new CraftItemEvent(recipe, inventoryView, type, p_9856_.getSlotNum(), click, action, p_9856_.getButtonNum());
                        } else {
                           event = new CraftItemEvent(recipe, inventoryView, type, p_9856_.getSlotNum(), click, action);
                        }
                     }
                  }

                  if (p_9856_.getSlotNum() == 3 && top instanceof SmithingInventory) {
                     org.bukkit.inventory.ItemStack result = ((SmithingInventory) top).getResult();
                     if (result != null) {
                        if (click == ClickType.NUMBER_KEY) {
                           event = new SmithItemEvent(inventoryView, type, p_9856_.getSlotNum(), click, action, p_9856_.getButtonNum());
                        } else {
                           event = new SmithItemEvent(inventoryView, type, p_9856_.getSlotNum(), click, action);
                        }
                     }
                  }

                  event.setCancelled(cancelled);
                  AbstractContainerMenu oldContainer = this.player.containerMenu; // SPIGOT-1224
                  cserver.getPluginManager().callEvent(event);
                  if (this.player.containerMenu != oldContainer) {
                     return;
                  }

                  switch (event.getResult()) {
                     case ALLOW:
                     case DEFAULT:
                        this.player.containerMenu.clicked(i, p_9856_.getButtonNum(), p_9856_.getClickType(), this.player);
                        break;
                     case DENY:
                        switch (action) {
                           // Modified other slots
                           case PICKUP_ALL:
                           case MOVE_TO_OTHER_INVENTORY:
                           case HOTBAR_MOVE_AND_READD:
                           case HOTBAR_SWAP:
                           case COLLECT_TO_CURSOR:
                           case UNKNOWN:
                              this.player.containerMenu.sendAllDataToRemote();
                              break;
                           // Modified cursor and clicked
                           case PICKUP_SOME:
                           case PICKUP_HALF:
                           case PICKUP_ONE:
                           case PLACE_ALL:
                           case PLACE_SOME:
                           case PLACE_ONE:
                           case SWAP_WITH_CURSOR:
                              this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, -1, this.player.inventoryMenu.incrementStateId(), this.player.containerMenu.getCarried()));
                              this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), p_9856_.getSlotNum(), this.player.containerMenu.getSlot(p_9856_.getSlotNum()).getItem()));
                              break;
                           // Modified clicked only
                           case DROP_ALL_SLOT:
                           case DROP_ONE_SLOT:
                              this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), p_9856_.getSlotNum(), this.player.containerMenu.getSlot(p_9856_.getSlotNum()).getItem()));
                              break;
                           // Modified cursor only
                           case DROP_ALL_CURSOR:
                           case DROP_ONE_CURSOR:
                           case CLONE_STACK:
                              this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, -1, this.player.inventoryMenu.incrementStateId(), this.player.containerMenu.getCarried()));
                              break;
                           // Nothing
                           case NOTHING:
                              break;
                        }
                  }

                  if (event instanceof CraftItemEvent || event instanceof SmithItemEvent) {
                     // Need to update the inventory on crafting to
                     // correctly support custom recipes
                     player.containerMenu.sendAllDataToRemote();
                  }
               }
               // CraftBukkit end

               for(Int2ObjectMap.Entry<ItemStack> entry : Int2ObjectMaps.fastIterable(p_9856_.getChangedSlots())) {
                  this.player.containerMenu.setRemoteSlotNoCopy(entry.getIntKey(), entry.getValue());
               }

               this.player.containerMenu.setRemoteCarried(p_9856_.getCarriedItem());
               this.player.containerMenu.resumeRemoteUpdates();
               if (flag) {
                  this.player.containerMenu.broadcastFullState();
               } else {
                  this.player.containerMenu.broadcastChanges();
               }

            }
         }
      }
   }

   public void handlePlaceRecipe(ServerboundPlaceRecipePacket p_9882_) {
      PacketUtils.ensureRunningOnSameThread(p_9882_, this, this.player.serverLevel());
      this.player.resetLastActionTime();
      if (!this.player.isSpectator() && this.player.containerMenu.containerId == p_9882_.getContainerId() && this.player.containerMenu instanceof RecipeBookMenu) {
         if (!this.player.containerMenu.stillValid(this.player)) {
            LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
         } else {
            // CraftBukkit start - implement PlayerRecipeBookClickEvent
            org.bukkit.inventory.Recipe recipe = this.cserver.getRecipe(CraftNamespacedKey.fromMinecraft(p_9882_.getRecipe()));
            if (recipe == null) {
               return;
            }
            org.bukkit.event.player.PlayerRecipeBookClickEvent event = CraftEventFactory.callRecipeBookClickEvent(this.player, recipe, p_9882_.isShiftDown());

            // Cast to keyed should be safe as the recipe will never be a MerchantRecipe.
            NamespacedKey namespacedKey = event.getRecipe() instanceof Keyed ? ((Keyed) recipe).getKey() : CraftNamespacedKey.fromMinecraft(p_9882_.getRecipe());
            this.server.getRecipeManager().byKey(CraftNamespacedKey.toMinecraft(namespacedKey)).ifPresent((irecipe) -> {
               ((RecipeBookMenu) this.player.containerMenu).handlePlacement(event.isShiftClick(), irecipe, this.player);
            });
         }
      }
   }

   public void handleContainerButtonClick(ServerboundContainerButtonClickPacket p_9854_) {
      PacketUtils.ensureRunningOnSameThread(p_9854_, this, this.player.serverLevel());
      if (this.player.isImmobile()) return; // CraftBukkit
      this.player.resetLastActionTime();
      if (this.player.containerMenu.containerId == p_9854_.getContainerId() && !this.player.isSpectator()) {
         if (!this.player.containerMenu.stillValid(this.player)) {
            LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
         } else {
            boolean flag = this.player.containerMenu.clickMenuButton(this.player, p_9854_.getButtonId());
            if (flag) {
               this.player.containerMenu.broadcastChanges();
            }

         }
      }
   }

   public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket p_9915_) {
      PacketUtils.ensureRunningOnSameThread(p_9915_, this, this.player.serverLevel());
      if (this.player.gameMode.isCreative()) {
         boolean flag = p_9915_.getSlotNum() < 0;
         ItemStack itemstack = p_9915_.getItem();
         if (!itemstack.isItemEnabled(this.player.level().enabledFeatures())) {
            return;
         }

         CompoundTag compoundtag = BlockItem.getBlockEntityData(itemstack);
         if (!itemstack.isEmpty() && compoundtag != null && compoundtag.contains("x") && compoundtag.contains("y") && compoundtag.contains("z")) {
            BlockPos blockpos = BlockEntity.getPosFromTag(compoundtag);
            if (this.player.level().isLoaded(blockpos)) {
               BlockEntity blockentity = this.player.level().getBlockEntity(blockpos);
               if (blockentity != null) {
                  blockentity.saveToItem(itemstack);
               }
            }
         }

         boolean flag1 = p_9915_.getSlotNum() >= 1 && p_9915_.getSlotNum() <= 45;
         boolean flag2 = itemstack.isEmpty() || itemstack.getDamageValue() >= 0 && itemstack.getCount() <= 64 && !itemstack.isEmpty();
         if (flag || (flag1 && !ItemStack.matches(this.player.inventoryMenu.getSlot(p_9915_.getSlotNum()).getItem(), p_9915_.getItem()))) { // Insist on valid slot
            // CraftBukkit start - Call click event
            InventoryView inventory = this.player.inventoryMenu.getBukkitView();
            org.bukkit.inventory.ItemStack item = CraftItemStack.asBukkitCopy(p_9915_.getItem());

            InventoryType.SlotType type = InventoryType.SlotType.QUICKBAR;
            if (flag) {
               type = InventoryType.SlotType.OUTSIDE;
            } else if (p_9915_.getSlotNum() < 36) {
               if (p_9915_.getSlotNum() >= 5 && p_9915_.getSlotNum() < 9) {
                  type = InventoryType.SlotType.ARMOR;
               } else {
                  type = InventoryType.SlotType.CONTAINER;
               }
            }
            InventoryCreativeEvent event = new InventoryCreativeEvent(inventory, type, flag ? -999 : p_9915_.getSlotNum(), item);
            cserver.getPluginManager().callEvent(event);

            itemstack = CraftItemStack.asNMSCopy(event.getCursor());

            switch (event.getResult()) {
               case ALLOW:
                  // Plugin cleared the id / stacksize checks
                  flag2 = true;
                  break;
               case DEFAULT:
                  break;
               case DENY:
                  // Reset the slot
                  if (p_9915_.getSlotNum() >= 0) {
                     this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.inventoryMenu.containerId, this.player.inventoryMenu.incrementStateId(), p_9915_.getSlotNum(), this.player.inventoryMenu.getSlot(p_9915_.getSlotNum()).getItem()));
                     this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, this.player.inventoryMenu.incrementStateId(), -1, ItemStack.EMPTY));
                  }
                  return;
            }
         }
         // CraftBukkit end
         if (flag1 && flag2) {
            this.player.inventoryMenu.getSlot(p_9915_.getSlotNum()).setByPlayer(itemstack);
            this.player.inventoryMenu.broadcastChanges();
         } else if (flag && flag2 && this.dropSpamTickCount < 200) {
            this.dropSpamTickCount += 20;
            this.player.drop(itemstack, true);
         }
      }

   }

   public void handleSignUpdate(ServerboundSignUpdatePacket p_9921_) {
      List<String> list = Stream.of(p_9921_.getLines()).map(ChatFormatting::stripFormatting).collect(Collectors.toList());
      this.filterTextPacket(list).thenAcceptAsync((p_215245_) -> {
         this.updateSignText(p_9921_, p_215245_);
      }, this.server);
   }

   private void updateSignText(ServerboundSignUpdatePacket p_9923_, List<FilteredText> p_9924_) {
      if (this.player.isImmobile()) return; // CraftBukkit
      this.player.resetLastActionTime();
      ServerLevel serverlevel = this.player.serverLevel();
      BlockPos blockpos = p_9923_.getPos();
      if (serverlevel.hasChunkAt(blockpos)) {
         BlockEntity blockentity = serverlevel.getBlockEntity(blockpos);
         if (!(blockentity instanceof SignBlockEntity)) {
            return;
         }

         SignBlockEntity signblockentity = (SignBlockEntity)blockentity;
         signblockentity.updateSignText(this.player, p_9923_.isFrontText(), p_9924_);
      }

   }

   public void handleKeepAlive(ServerboundKeepAlivePacket p_9870_) {
      PacketUtils.ensureRunningOnSameThread(p_9870_, this, this.player.serverLevel()); // CraftBukkit
      if (this.keepAlivePending && p_9870_.getId() == this.keepAliveChallenge) {
         int i = (int)(Util.getMillis() - this.keepAliveTime);
         this.player.latency = (this.player.latency * 3 + i) / 4;
         this.keepAlivePending = false;
      } else if (!this.isSingleplayerOwner()) {
         this.disconnect(Component.translatable("disconnect.timeout"));
      }

   }

   public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket p_9887_) {
      PacketUtils.ensureRunningOnSameThread(p_9887_, this, this.player.serverLevel());
      // CraftBukkit start
      if (this.player.getAbilities().mayfly && this.player.getAbilities().flying != p_9887_.isFlying()) {
         PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(this.player.getBukkitEntity(), p_9887_.isFlying());
         this.cserver.getPluginManager().callEvent(event);
         if (!event.isCancelled()) {
            this.player.getAbilities().flying = p_9887_.isFlying(); // Actually set the player's flying status
         } else {
            this.player.onUpdateAbilities(); // Tell the player their ability was reverted
         }
      }
      // CraftBukkit end
   }

   public void handleClientInformation(ServerboundClientInformationPacket p_9845_) {
      PacketUtils.ensureRunningOnSameThread(p_9845_, this, this.player.serverLevel());
      this.player.updateOptions(p_9845_);
   }

   // CraftBukkit start
   private static final ResourceLocation CUSTOM_REGISTER = new ResourceLocation("register");
   private static final ResourceLocation CUSTOM_UNREGISTER = new ResourceLocation("unregister");
   // Mohist start
   public void handleCustomPayloadMohist(final ServerboundCustomPayloadPacket p_9860_) {
      var readerIndex = p_9860_.data.readerIndex();
      var buf = new byte[p_9860_.data.readableBytes()];
      p_9860_.data.readBytes(buf);
      p_9860_.data.readerIndex(readerIndex);
      ServerLifecycleHooks.getCurrentServer().executeIfPossible(new Runnable() {
          @Override
          public void run() {
              if (ServerLifecycleHooks.getCurrentServer().hasStopped() || processedDisconnect) {
                  return;
              }
              if (ServerGamePacketListenerImpl.this.connection.isConnected()) {
                  if (p_9860_.identifier.equals(CUSTOM_REGISTER)) {
                      try {
                          String channels = new String(buf, StandardCharsets.UTF_8);
                          for (String channel : channels.split("\0")) {
                              if (!StringUtil.isNullOrEmpty(channel)) {
                                  ServerGamePacketListenerImpl.this.getCraftPlayer().addChannel(channel);
                              }
                          }
                      } catch (Exception ex) {
                          LOGGER.error("Couldn't register custom payload", ex);
                          ServerGamePacketListenerImpl.this.disconnect("Invalid payload REGISTER!");
                      }
                  } else if (p_9860_.identifier.equals(CUSTOM_UNREGISTER)) {
                      try {
                          String channels = new String(buf, StandardCharsets.UTF_8);
                          for (String channel : channels.split("\0")) {
                              if (!StringUtil.isNullOrEmpty(channel)) {
                                  ServerGamePacketListenerImpl.this.getCraftPlayer().removeChannel(channel);
                              }
                          }
                      } catch (Exception ex) {
                          LOGGER.error("Couldn't unregister custom payload", ex);
                          ServerGamePacketListenerImpl.this.disconnect("Invalid payload UNREGISTER!");
                      }
                  } else {
                      try {
                          ServerGamePacketListenerImpl.this.cserver.getMessenger().dispatchIncomingMessage(ServerGamePacketListenerImpl.this.player.getBukkitEntity(), p_9860_.identifier.toString(), buf);
                      } catch (Exception ex) {
                          LOGGER.error("Couldn't dispatch custom payload", ex);
                          ServerGamePacketListenerImpl.this.disconnect("Invalid custom payload!");
                      }
                  }
              }
          }
      });
   }
   // Mohist end
   public void handleCustomPayload(ServerboundCustomPayloadPacket p_9860_) {
      PacketUtils.ensureRunningOnSameThread(p_9860_, this, this.player.serverLevel());
      if (!ServerAPI.hasMod("fabric_api")) {
         var readerIndex = p_9860_.data.readerIndex();
         var buf = new byte[p_9860_.data.readableBytes()];
         p_9860_.data.readBytes(buf);
         p_9860_.data.readerIndex(readerIndex);
         ServerLifecycleHooks.getCurrentServer().executeIfPossible(new Runnable() {
             @Override
             public void run() {
                 if (ServerLifecycleHooks.getCurrentServer().hasStopped() || processedDisconnect) {
                     return;
                 }

                 if (ServerGamePacketListenerImpl.this.connection.isConnected()) {
                     if (p_9860_.identifier.equals(CUSTOM_REGISTER)) {
                         try {
                             String channels = new String(buf, StandardCharsets.UTF_8);
                             for (String channel : channels.split("\0")) {
                                 if (!StringUtil.isNullOrEmpty(channel)) {
                                     ServerGamePacketListenerImpl.this.getCraftPlayer().addChannel(channel);
                                 }
                             }
                         } catch (Exception ex) {
                             LOGGER.error("Couldn't register custom payload", ex);
                             ServerGamePacketListenerImpl.this.disconnect("Invalid payload REGISTER!");
                         }
                     } else if (p_9860_.identifier.equals(CUSTOM_UNREGISTER)) {
                         try {
                             String channels = new String(buf, StandardCharsets.UTF_8);
                             for (String channel : channels.split("\0")) {
                                 if (!StringUtil.isNullOrEmpty(channel)) {
                                     ServerGamePacketListenerImpl.this.getCraftPlayer().removeChannel(channel);
                                 }
                             }
                         } catch (Exception ex) {
                             LOGGER.error("Couldn't unregister custom payload", ex);
                             ServerGamePacketListenerImpl.this.disconnect("Invalid payload UNREGISTER!");
                         }
                     } else {
                         try {
                             ServerGamePacketListenerImpl.this.cserver.getMessenger().dispatchIncomingMessage(ServerGamePacketListenerImpl.this.player.getBukkitEntity(), p_9860_.identifier.toString(), buf);
                         } catch (Exception ex) {
                             LOGGER.error("Couldn't dispatch custom payload", ex);
                             ServerGamePacketListenerImpl.this.disconnect("Invalid custom payload!");
                         }
                     }
                 }
             }
         });
      }
      net.minecraftforge.network.NetworkHooks.onCustomPayload(p_9860_, this.connection);
   }

   public void handleChangeDifficulty(ServerboundChangeDifficultyPacket p_9839_) {
      PacketUtils.ensureRunningOnSameThread(p_9839_, this, this.player.serverLevel());
      if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
         this.server.setDifficulty(p_9839_.getDifficulty(), false);
      }
   }

   public void handleLockDifficulty(ServerboundLockDifficultyPacket p_9872_) {
      PacketUtils.ensureRunningOnSameThread(p_9872_, this, this.player.serverLevel());
      if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
         this.server.setDifficultyLocked(p_9872_.isLocked());
      }
   }

   public void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket p_253950_) {
      if (true) return;
      PacketUtils.ensureRunningOnSameThread(p_253950_, this, this.player.serverLevel());
      RemoteChatSession.Data remotechatsession$data = p_253950_.chatSession();
      ProfilePublicKey.Data profilepublickey$data = this.chatSession != null ? this.chatSession.profilePublicKey().data() : null;
      ProfilePublicKey.Data profilepublickey$data1 = remotechatsession$data.profilePublicKey();
      if (!Objects.equals(profilepublickey$data, profilepublickey$data1)) {
         if (profilepublickey$data != null && profilepublickey$data1.expiresAt().isBefore(profilepublickey$data.expiresAt())) {
            this.disconnect(ProfilePublicKey.EXPIRED_PROFILE_PUBLIC_KEY);
         } else {
            try {
               SignatureValidator signaturevalidator = this.server.getProfileKeySignatureValidator();
               if (signaturevalidator == null) {
                  LOGGER.warn("Ignoring chat session from {} due to missing Services public key", (Object)this.player.getGameProfile().getName());
                  return;
               }

               this.resetPlayerChatState(remotechatsession$data.validate(this.player.getGameProfile(), signaturevalidator, Duration.ZERO));
            } catch (ProfilePublicKey.ValidationException profilepublickey$validationexception) {
               LOGGER.error("Failed to validate profile key: {}", (Object)profilepublickey$validationexception.getMessage());
               this.disconnect(profilepublickey$validationexception.getComponent());
            }

         }
      }
   }

   private void resetPlayerChatState(RemoteChatSession p_253823_) {
      this.chatSession = p_253823_;
      this.signedMessageDecoder = p_253823_.createMessageDecoder(this.player.getUUID());
      this.chatMessageChain.append((p_253488_) -> {
         this.player.setChatSession(p_253823_);
         this.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT), List.of(this.player)));
         return CompletableFuture.completedFuture((Object)null);
      });
   }

   public ServerPlayer getPlayer() {
      return this.player;
   }

   @FunctionalInterface
   interface EntityInteraction {
      InteractionResult run(ServerPlayer p_143695_, Entity p_143696_, InteractionHand p_143697_);
   }

   public final boolean isDisconnected() {
      return !this.player.joining && !this.connection.isConnected();
   }

   public void handleCommand(String s) {
      if ( org.spigotmc.SpigotConfig.logCommands ) // Spigot
      LOGGER.info(this.player.getScoreboardName() + " issued server command: " + s);

      CraftPlayer player = this.getCraftPlayer();

      PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, s, new LazyPlayerSet(server));
      this.cserver.getPluginManager().callEvent(event);

      if (event.isCancelled()) {
         return;
      }

      try {
         if (this.cserver.dispatchCommand(event.getPlayer(), event.getMessage().substring(1))) {
            return;
         }
      } catch (org.bukkit.command.CommandException ex) {
         player.sendMessage(org.bukkit.ChatColor.RED + "An internal error occurred while attempting to perform this command");
         java.util.logging.Logger.getLogger(ServerGamePacketListenerImpl.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
         return;
      }
   }
   // CraftBukkit end
}
