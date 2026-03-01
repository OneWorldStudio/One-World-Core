package net.minecraft.server.level;

import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.oneworldstudiomc.ai.koukou.KouKou;
import com.oneworldstudiomc.bukkit.inventory.MohistModsInventory;
import com.oneworldstudiomc.paper.event.packet.PlayerChunkLoadEvent;
import com.oneworldstudiomc.paper.event.packet.PlayerChunkUnloadEvent;
import com.oneworldstudiomc.plugins.KeepInventory;
import com.oneworldstudiomc.plugins.world.WorldManage;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil.FoundRectangle;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.PositionImpl;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType.Bound;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerStatus.Favicon;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ComplexItem;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock.DoubleInventory;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerContainerEvent.Close;
import net.minecraftforge.event.entity.player.PlayerContainerEvent.Open;
import net.minecraftforge.event.entity.player.PlayerEvent.TabListNameFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorldBorder;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftPortalEvent;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.util.BlockStateListPopulator;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftLocation;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedMainHandEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent.Cause;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.PortalCreateEvent.CreateReason;
import org.bukkit.inventory.MainHand;
import org.slf4j.Logger;

import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;

public class ServerPlayer extends Player {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
   private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
   public ServerGamePacketListenerImpl connection;
   public final MinecraftServer server;
   public final ServerPlayerGameMode gameMode;
   private final PlayerAdvancements advancements;
   private final ServerStatsCounter stats;
   private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
   private int lastRecordedFoodLevel = Integer.MIN_VALUE;
   private int lastRecordedAirLevel = Integer.MIN_VALUE;
   private int lastRecordedArmor = Integer.MIN_VALUE;
   private int lastRecordedLevel = Integer.MIN_VALUE;
   private int lastRecordedExperience = Integer.MIN_VALUE;
   private float lastSentHealth = -1.0E8F;
   private int lastSentFood = -99999999;
   private boolean lastFoodSaturationZero = true;
   public int lastSentExp = -99999999;
   public int spawnInvulnerableTime = 60;
   private ChatVisiblity chatVisibility = ChatVisiblity.FULL;
   private boolean canChatColor = true;
   private long lastActionTime = Util.getMillis();
   @Nullable
   private Entity camera;
   public boolean isChangingDimension;
   private boolean seenCredits;
   private final ServerRecipeBook recipeBook = new ServerRecipeBook();
   @Nullable
   private Vec3 levitationStartPos;
   private int levitationStartTime;
   private boolean disconnected;
   @Nullable
   private Vec3 startingToFallPosition;
   @Nullable
   private Vec3 enteredNetherPosition;
   @Nullable
   private Vec3 enteredLavaOnVehiclePosition;
   private SectionPos lastSectionPos = SectionPos.of(0, 0, 0);
   private ResourceKey<Level> respawnDimension = Level.OVERWORLD;
   @Nullable
   private BlockPos respawnPosition;
   private boolean respawnForced;
   private float respawnAngle;
   private final TextFilter textFilter;
   private boolean textFilteringEnabled;
   private boolean allowsListing = true;
   private WardenSpawnTracker wardenSpawnTracker = new WardenSpawnTracker(0, 0, 0);
   private final ContainerSynchronizer containerSynchronizer = new ContainerSynchronizer() {
      public void sendInitialData(AbstractContainerMenu p_143448_, NonNullList<ItemStack> p_143449_, ItemStack p_143450_, int[] p_143451_) {
         ServerPlayer.this.connection.send(new ClientboundContainerSetContentPacket(p_143448_.containerId, p_143448_.incrementStateId(), p_143449_, p_143450_));

         for(int i = 0; i < p_143451_.length; ++i) {
            this.broadcastDataValue(p_143448_, i, p_143451_[i]);
         }

      }

      public void sendSlotChange(AbstractContainerMenu p_143441_, int p_143442_, ItemStack p_143443_) {
         ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(p_143441_.containerId, p_143441_.incrementStateId(), p_143442_, p_143443_));
      }

      public void sendCarriedChange(AbstractContainerMenu p_143445_, ItemStack p_143446_) {
         ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(-1, p_143445_.incrementStateId(), -1, p_143446_));
      }

      public void sendDataChange(AbstractContainerMenu p_143437_, int p_143438_, int p_143439_) {
         this.broadcastDataValue(p_143437_, p_143438_, p_143439_);
      }

      private void broadcastDataValue(AbstractContainerMenu p_143455_, int p_143456_, int p_143457_) {
         ServerPlayer.this.connection.send(new ClientboundContainerSetDataPacket(p_143455_.containerId, p_143456_, p_143457_));
      }
   };
   private final ContainerListener containerListener = new ContainerListener() {
      public void slotChanged(AbstractContainerMenu p_143466_, int p_143467_, ItemStack p_143468_) {
         Slot slot = p_143466_.getSlot(p_143467_);
         if (!(slot instanceof ResultSlot)) {
            if (slot.container == ServerPlayer.this.getInventory()) {
               CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), p_143468_);
            }

         }
      }

      public void dataChanged(AbstractContainerMenu p_143462_, int p_143463_, int p_143464_) {
      }
   };
   @Nullable
   private RemoteChatSession chatSession;
   public int containerCounter;
   public int latency;
   public boolean wonGame;

   // CraftBukkit start
   public String displayName;
   public Location compassTarget;
   public int newExp = 0;
   public int newLevel = 0;
   public int newTotalExp = 0;
   public boolean keepLevel = false;
   public double maxHealthCache;
   public boolean joining = true;
   public boolean sentListPacket = false;
   public Integer clientViewDistance;
   public String kickLeaveMessage = null; // SPIGOT-3034: Forward leave message to PlayerQuitEvent
   // CraftBukkit end

   // Mohist start
   public boolean initialized = false;
   // Mohist end

   public ServerPlayer(MinecraftServer p_254143_, ServerLevel p_254435_, GameProfile p_253651_) {
      super(p_254435_, p_254435_.getSharedSpawnPos(), p_254435_.getSharedSpawnAngle(), p_253651_);
      this.textFilter = p_254143_.createTextFilterForPlayer(this);
      this.gameMode = p_254143_.createGameModeForPlayer(this);
      this.server = p_254143_;
      this.stats = p_254143_.getPlayerList().getPlayerStats(this);
      this.advancements = p_254143_.getPlayerList().getPlayerAdvancements(this);
      this.setMaxUpStep(1.0F);
      this.fudgeSpawnLocation(p_254435_);

      // CraftBukkit start
      this.displayName = this.getScoreboardName();
      this.bukkitPickUpLoot = true;
      this.maxHealthCache = this.getMaxHealth();
      this.initialized = true;
   }

   // Yes, this doesn't match Vanilla, but it's the best we can do for now.
   // If this is an issue, PRs are welcome
   public final BlockPos getSpawnPoint(ServerLevel worldserver) {
      BlockPos blockposition = worldserver.getSharedSpawnPos();

      if (worldserver.dimensionType().hasSkyLight() && worldserver.serverLevelData.getGameType() != GameType.ADVENTURE) {
         int i = Math.max(0, this.server.getSpawnRadius(worldserver));
         int j = Mth.floor(worldserver.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

         if (j < i) {
            i = j;
         }

         if (j <= 1) {
            i = 1;
         }

         long k = (long) (i * 2 + 1);
         long l = k * k;
         int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
         int j1 = this.getCoprime(i1);
         int k1 = RandomSource.create().nextInt(i1);

         for (int l1 = 0; l1 < i1; ++l1) {
            int i2 = (k1 + j1 * l1) % i1;
            int j2 = i2 % (i * 2 + 1);
            int k2 = i2 / (i * 2 + 1);
            BlockPos blockposition1 = PlayerRespawnLogic.getOverworldRespawnPos(worldserver, blockposition.getX() + j2 - i, blockposition.getZ() + k2 - i);

            if (blockposition1 != null) {
               return blockposition1;
            }
         }
      }

      return blockposition;
   }
   // CraftBukkit end

   private void fudgeSpawnLocation(ServerLevel p_9202_) {
      BlockPos blockpos = p_9202_.getSharedSpawnPos();
      if (p_9202_.dimensionType().hasSkyLight() && (p_9202_.K == null ? p_9202_.getServer().getWorldData().getGameType() != GameType.ADVENTURE : p_9202_.K.getGameType() != GameType.ADVENTURE)) {
         int i = Math.max(0, this.server.getSpawnRadius(p_9202_));
         int j = Mth.floor(p_9202_.getWorldBorder().getDistanceToBorder((double)blockpos.getX(), (double)blockpos.getZ()));
         if (j < i) {
            i = j;
         }

         if (j <= 1) {
            i = 1;
         }

         long k = (long)(i * 2 + 1);
         long l = k * k;
         int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int)l;
         int j1 = this.getCoprime(i1);
         int k1 = RandomSource.create().nextInt(i1);

         for(int l1 = 0; l1 < i1; ++l1) {
            int i2 = (k1 + j1 * l1) % i1;
            int j2 = i2 % (i * 2 + 1);
            int k2 = i2 / (i * 2 + 1);
            BlockPos blockpos1 = PlayerRespawnLogic.getOverworldRespawnPos(p_9202_, blockpos.getX() + j2 - i, blockpos.getZ() + k2 - i);
            if (blockpos1 != null) {
               this.moveTo(blockpos1, 0.0F, 0.0F);
               if (p_9202_.noCollision(this)) {
                  break;
               }
            }
         }
      } else {
         this.moveTo(blockpos, 0.0F, 0.0F);

         while(!p_9202_.noCollision(this) && this.getY() < (double)(p_9202_.getMaxBuildHeight() - 1)) {
            this.setPos(this.getX(), this.getY() + 1.0D, this.getZ());
         }
      }

   }

   private int getCoprime(int p_9238_) {
      return p_9238_ <= 16 ? p_9238_ - 1 : 17;
   }

   public void readAdditionalSaveData(CompoundTag p_9131_) {
      super.readAdditionalSaveData(p_9131_);
      if (p_9131_.contains("warden_spawn_tracker", 10)) {
         WardenSpawnTracker.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, p_9131_.get("warden_spawn_tracker"))).resultOrPartial(LOGGER::error).ifPresent((p_248205_) -> {
            this.wardenSpawnTracker = p_248205_;
         });
      }

      if (p_9131_.contains("enteredNetherPosition", 10)) {
         CompoundTag compoundtag = p_9131_.getCompound("enteredNetherPosition");
         this.enteredNetherPosition = new Vec3(compoundtag.getDouble("x"), compoundtag.getDouble("y"), compoundtag.getDouble("z"));
      }

      this.seenCredits = p_9131_.getBoolean("seenCredits");
      if (p_9131_.contains("recipeBook", 10)) {
         this.recipeBook.fromNbt(p_9131_.getCompound("recipeBook"), this.server.getRecipeManager());
      }
      this.getBukkitEntity().readExtraData(p_9131_); // CraftBukkit

      if (this.isSleeping()) {
         this.stopSleeping();
      }

      // CraftBukkit start
      String spawnWorld = p_9131_.getString("SpawnWorld");
      CraftWorld oldWorld = (CraftWorld) Bukkit.getWorld(spawnWorld);
      if (oldWorld != null) {
         this.respawnDimension = oldWorld.getHandle().dimension();
      }
      // CraftBukkit end

      if (p_9131_.contains("SpawnX", 99) && p_9131_.contains("SpawnY", 99) && p_9131_.contains("SpawnZ", 99)) {
         this.respawnPosition = new BlockPos(p_9131_.getInt("SpawnX"), p_9131_.getInt("SpawnY"), p_9131_.getInt("SpawnZ"));
         this.respawnForced = p_9131_.getBoolean("SpawnForced");
         this.respawnAngle = p_9131_.getFloat("SpawnAngle");
         if (p_9131_.contains("SpawnDimension")) {
            this.respawnDimension = Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, p_9131_.get("SpawnDimension")).resultOrPartial(LOGGER::error).orElse(Level.OVERWORLD);
         }
      }

   }

   public void addAdditionalSaveData(CompoundTag p_9197_) {
      super.addAdditionalSaveData(p_9197_);
      WardenSpawnTracker.CODEC.encodeStart(NbtOps.INSTANCE, this.wardenSpawnTracker).resultOrPartial(LOGGER::error).ifPresent((p_9134_) -> {
         p_9197_.put("warden_spawn_tracker", p_9134_);
      });
      this.storeGameTypes(p_9197_);
      p_9197_.putBoolean("seenCredits", this.seenCredits);
      if (this.enteredNetherPosition != null) {
         CompoundTag compoundtag = new CompoundTag();
         compoundtag.putDouble("x", this.enteredNetherPosition.x);
         compoundtag.putDouble("y", this.enteredNetherPosition.y);
         compoundtag.putDouble("z", this.enteredNetherPosition.z);
         p_9197_.put("enteredNetherPosition", compoundtag);
      }

      Entity entity1 = this.getRootVehicle();
      Entity entity = this.getVehicle();
      // CraftBukkit start - handle non-persistent vehicles
      boolean persistVehicle = true;
      if (entity != null) {
         Entity vehicle;
         for (vehicle = entity; vehicle != null; vehicle = vehicle.getVehicle()) {
            if (!vehicle.persist) {
               persistVehicle = false;
               break;
            }
         }
      }

      if (persistVehicle && entity != null && entity1 != this && entity1.hasExactlyOnePlayerPassenger()) {
         // CraftBukkit end
         CompoundTag compoundtag1 = new CompoundTag();
         CompoundTag compoundtag2 = new CompoundTag();
         entity1.save(compoundtag2);
         compoundtag1.putUUID("Attach", entity.getUUID());
         compoundtag1.put("Entity", compoundtag2);
         p_9197_.put("RootVehicle", compoundtag1);
      }

      p_9197_.put("recipeBook", this.recipeBook.toNbt());
      p_9197_.putString("Dimension", this.level.dimension().location().toString());
      if (this.respawnPosition != null) {
         p_9197_.putInt("SpawnX", this.respawnPosition.getX());
         p_9197_.putInt("SpawnY", this.respawnPosition.getY());
         p_9197_.putInt("SpawnZ", this.respawnPosition.getZ());
         p_9197_.putBoolean("SpawnForced", this.respawnForced);
         p_9197_.putFloat("SpawnAngle", this.respawnAngle);
         ResourceLocation.CODEC.encodeStart(NbtOps.INSTANCE, this.respawnDimension.location()).resultOrPartial(LOGGER::error).ifPresent((p_248207_) -> {
            p_9197_.put("SpawnDimension", p_248207_);
         });
      }
      this.getBukkitEntity().setExtraData(p_9197_); // CraftBukkit

   }

   // CraftBukkit start - World fallback code, either respawn location or global spawn
   public void spawnIn(Level world) {
      this.level = world;
      if (world == null) {
         this.revive();
         Vec3 position = null;
         if (this.respawnDimension != null) {
            world = this.server.getLevel(this.respawnDimension);
            if (world != null && this.getRespawnPosition() != null) {
               position = Player.findRespawnPositionAndUseSpawnBlock((ServerLevel) world, this.getRespawnPosition(), this.getRespawnAngle(), false, false).orElse(null);
            }
         }
         if (world == null || position == null) {
            world = ((CraftWorld) Bukkit.getServer().getWorlds().get(0)).getHandle();
            position = Vec3.atCenterOf(world.getSharedSpawnPos());
         }
         this.level = world;
         this.setPos(position);
      }
      this.gameMode.setLevel((ServerLevel) world);
   }
   // CraftBukkit end

   public void setExperiencePoints(int p_8986_) {
      float f = (float)this.getXpNeededForNextLevel();
      float f1 = (f - 1.0F) / f;
      this.experienceProgress = Mth.clamp((float)p_8986_ / f, 0.0F, f1);
      this.lastSentExp = -1;
   }

   public void setExperienceLevels(int p_9175_) {
      this.experienceLevel = p_9175_;
      this.lastSentExp = -1;
   }

   public void giveExperienceLevels(int p_9200_) {
      super.giveExperienceLevels(p_9200_);
      this.lastSentExp = -1;
   }

   public void onEnchantmentPerformed(ItemStack p_9079_, int p_9080_) {
      super.onEnchantmentPerformed(p_9079_, p_9080_);
      this.lastSentExp = -1;
   }

   public void initMenu(AbstractContainerMenu p_143400_) {
      p_143400_.addSlotListener(this.containerListener);
      p_143400_.setSynchronizer(this.containerSynchronizer);
   }

   public void initInventoryMenu() {
      this.initMenu(this.inventoryMenu);
   }

   public void onEnterCombat() {
      super.onEnterCombat();
      this.connection.send(new ClientboundPlayerCombatEnterPacket());
   }

   public void onLeaveCombat() {
      super.onLeaveCombat();
      this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
   }

   protected void onInsideBlock(BlockState p_9103_) {
      CriteriaTriggers.ENTER_BLOCK.trigger(this, p_9103_);
   }

   protected ItemCooldowns createItemCooldowns() {
      return new ServerItemCooldowns(this);
   }

   public void tick() {
      // CraftBukkit start
      if (this.joining) {
         this.joining = false;
      }
      // CraftBukkit end
      this.gameMode.tick();
      this.wardenSpawnTracker.tick();
      --this.spawnInvulnerableTime;
      if (this.invulnerableTime > 0) {
         --this.invulnerableTime;
      }

      this.containerMenu.broadcastChanges();
      if (!this.level().isClientSide && !this.containerMenu.stillValid(this)) {
         this.closeContainer();
         this.containerMenu = this.inventoryMenu;
      }

      Entity entity = this.getCamera();
      if (entity != this) {
         if (entity.isAlive()) {
            this.absMoveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
            this.serverLevel().getChunkSource().move(this);
            if (this.wantsToStopRiding()) {
               this.setCamera(this);
            }
         } else {
            this.setCamera(this);
         }
      }

      CriteriaTriggers.TICK.trigger(this);
      if (this.levitationStartPos != null) {
         CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
      }

      this.trackStartFallingPosition();
      this.trackEnteredOrExitedLavaOnVehicle();
      this.advancements.flushDirty(this);
   }

   public void doTick() {
      try {
         if (!this.isSpectator() || !this.touchingUnloadedChunk()) {
            super.tick();
         }

         for(int i = 0; i < this.getInventory().getContainerSize(); ++i) {
            ItemStack itemstack = this.getInventory().getItem(i);
            if (itemstack.getItem().isComplex()) {
               Packet<?> packet = ((ComplexItem)itemstack.getItem()).getUpdatePacket(itemstack, this.level(), this);
               if (packet != null) {
                  this.connection.send(packet);
               }
            }
         }

         if (this.getHealth() != this.lastSentHealth || this.lastSentFood != this.foodData.getFoodLevel() || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
            this.connection.send(new ClientboundSetHealthPacket(this.getBukkitEntity().getScaledHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel())); // CraftBukkit
            this.lastSentHealth = this.getHealth();
            this.lastSentFood = this.foodData.getFoodLevel();
            this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
         }

         if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
            this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
            this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
         }

         if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
            this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
            this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float)this.lastRecordedFoodLevel));
         }

         if (this.getAirSupply() != this.lastRecordedAirLevel) {
            this.lastRecordedAirLevel = this.getAirSupply();
            this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float)this.lastRecordedAirLevel));
         }

         if (this.getArmorValue() != this.lastRecordedArmor) {
            this.lastRecordedArmor = this.getArmorValue();
            this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float)this.lastRecordedArmor));
         }

         if (this.totalExperience != this.lastRecordedExperience) {
            this.lastRecordedExperience = this.totalExperience;
            this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float)this.lastRecordedExperience));
         }

         // CraftBukkit start - Force max health updates
         if (this.maxHealthCache != this.getMaxHealth()) {
            this.getBukkitEntity().updateScaledHealth();
         }
         // CraftBukkit end

         if (this.experienceLevel != this.lastRecordedLevel) {
            this.lastRecordedLevel = this.experienceLevel;
            this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float)this.lastRecordedLevel));
         }

         if (this.totalExperience != this.lastSentExp) {
            this.lastSentExp = this.totalExperience;
            this.connection.send(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
         }

         if (this.tickCount % 20 == 0) {
            CriteriaTriggers.LOCATION.trigger(this);
         }

         // CraftBukkit start - initialize oldLevel, fire PlayerLevelChangeEvent, and tick client-sided world border
         if (this.oldLevel == -1) {
            this.oldLevel = this.experienceLevel;
         }

         if (this.oldLevel != this.experienceLevel) {
            CraftEventFactory.callPlayerLevelChangeEvent(this.getBukkitEntity(), this.oldLevel, this.experienceLevel);
            this.oldLevel = this.experienceLevel;
         }

         if (this.getBukkitEntity().hasClientWorldBorder()) {
            ((CraftWorldBorder) this.getBukkitEntity().getWorldBorder()).getHandle().tick();
         }
         // CraftBukkit end

      } catch (Throwable throwable) {
         throwable.fillInStackTrace();
         CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking player");
         CrashReportCategory crashreportcategory = crashreport.addCategory("Player being ticked");
         this.fillCrashReportCategory(crashreportcategory);
         throw new ReportedException(crashreport);
      }
   }

   public void resetFallDistance() {
      if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
         CriteriaTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
      }

      this.startingToFallPosition = null;
      super.resetFallDistance();
   }

   public void trackStartFallingPosition() {
      if (this.fallDistance > 0.0F && this.startingToFallPosition == null) {
         this.startingToFallPosition = this.position();
      }

   }

   public void trackEnteredOrExitedLavaOnVehicle() {
      if (this.getVehicle() != null && this.getVehicle().isInLava()) {
         if (this.enteredLavaOnVehiclePosition == null) {
            this.enteredLavaOnVehiclePosition = this.position();
         } else {
            CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
         }
      }

      if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
         this.enteredLavaOnVehiclePosition = null;
      }

   }

   private void updateScoreForCriteria(ObjectiveCriteria p_9105_, int p_9106_) {
      this.level.getCraftServer().getScoreboardManager().getScoreboardScores(p_9105_, this.getScoreboardName(), (p_9178_) -> {
         p_9178_.setScore(p_9106_);
      });
   }

   public void die(DamageSource p_9035_) {
      this.gameEvent(GameEvent.ENTITY_DIE);
      if (net.minecraftforge.common.ForgeHooks.onLivingDeath(this, p_9035_)) return;
      boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
      // CraftBukkit start - fire PlayerDeathEvent
      if (this.isRemoved()) {
         return;
      }

      List<org.bukkit.inventory.ItemStack> loot = new ArrayList<org.bukkit.inventory.ItemStack>(this.getInventory().getContainerSize());
      boolean keepInventory = KeepInventory.inventory(this) || this.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || this.isSpectator();

      if (!keepInventory) {
         Collection<ItemEntity> drops = this.captureDrops(null);
         if (drops != null) {
            for (ItemEntity entity : drops) {
               CraftItemStack craftItemStack = CraftItemStack.asCraftMirror(entity.getItem());
               loot.add(craftItemStack);
            }
         }
      }

      Component defaultMessage = this.getCombatTracker().getDeathMessage();
      String deathmessage = defaultMessage.getString();

      keepLevel = keepInventory; // SPIGOT-2222: pre-set keepLevel
      org.bukkit.event.entity.PlayerDeathEvent event = CraftEventFactory.callPlayerDeathEvent(this, loot, deathmessage, keepLevel);

      // SPIGOT-943 - only call if they have an inventory open
      if (this.containerMenu != this.inventoryMenu) {
         this.closeContainer();
      }

      String deathMessage = event.getDeathMessage();
      if (deathMessage != null && deathMessage.length() > 0 && flag) { // TODO: allow plugins to override?
         Component component;
         if (deathMessage.equals(deathmessage)) {
            component = this.getCombatTracker().getDeathMessage();
         } else {
            component = CraftChatMessage.fromStringOrNull(deathMessage);
         }
         this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), component), PacketSendListener.exceptionallySend(() -> {
            int i = 256;
            String s = component.getString(256);
            Component component1 = Component.translatable("death.attack.message_too_long", Component.literal(s).withStyle(ChatFormatting.YELLOW));
            Component component2 = Component.translatable("death.attack.even_more_magic", this.getDisplayName()).withStyle((p_143420_) -> {
               return p_143420_.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, component1));
            });
            return new ClientboundPlayerCombatKillPacket(this.getId(), component2);
         }));
         Team team = this.getTeam();
         if (team != null && team.getDeathMessageVisibility() != Team.Visibility.ALWAYS) {
            if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
               this.server.getPlayerList().broadcastSystemToTeam(this, component);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
               this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, component);
            }
         } else {
            this.server.getPlayerList().broadcastSystemMessage(component, false);
            KouKou.death(component.getString());
         }
      } else {
         this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
      }

      this.removeEntitiesOnShoulder();
      if (this.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
         this.tellNeutralMobsThatIDied();
      }

      // we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.
      if (!event.getKeepInventory()) {
         dropAllDeathLoot(p_9035_);
      }

      this.setCamera(this); // Remove spectated target
      // CraftBukkit end

      // CraftBukkit - Get our scores instead
      this.level.getCraftServer().getScoreboardManager().getScoreboardScores(ObjectiveCriteria.DEATH_COUNT, this.getScoreboardName(), Score::increment);
      LivingEntity livingentity = this.getKillCredit();
      if (livingentity != null) {
         this.awardStat(Stats.ENTITY_KILLED_BY.get(livingentity.getType()));
         livingentity.awardKillScore(this, this.deathScore, p_9035_);
         this.createWitherRose(livingentity);
      }

      this.level().broadcastEntityEvent(this, (byte)3);
      this.awardStat(Stats.DEATHS);
      this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
      this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
      this.clearFire();
      this.setTicksFrozen(0);
      this.setSharedFlagOnFire(false);
      this.getCombatTracker().recheckStatus();
      this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
   }

   private void tellNeutralMobsThatIDied() {
      AABB aabb = (new AABB(this.blockPosition())).inflate(32.0D, 10.0D, 32.0D);
      this.level().getEntitiesOfClass(Mob.class, aabb, EntitySelector.NO_SPECTATORS).stream().filter((p_9188_) -> {
         return p_9188_ instanceof NeutralMob;
      }).forEach((p_9057_) -> {
         ((NeutralMob)p_9057_).playerDied(this);
      });
   }

   public void awardKillScore(Entity p_9050_, int p_9051_, DamageSource p_9052_) {
      if (p_9050_ != this) {
         super.awardKillScore(p_9050_, p_9051_, p_9052_);
         this.increaseScore(p_9051_);
         String s = this.getScoreboardName();
         String s1 = p_9050_.getScoreboardName();
         // CraftBukkit - Get our scores instead
         this.level.getCraftServer().getScoreboardManager().getScoreboardScores(ObjectiveCriteria.KILL_COUNT_ALL, s, Score::increment);
         if (p_9050_ instanceof Player) {
            this.awardStat(Stats.PLAYER_KILLS);
            // CraftBukkit - Get our scores instead
            this.level.getCraftServer().getScoreboardManager().getScoreboardScores(ObjectiveCriteria.KILL_COUNT_PLAYERS, s, Score::increment);
         } else {
            this.awardStat(Stats.MOB_KILLS);
         }

         this.handleTeamKill(s, s1, ObjectiveCriteria.TEAM_KILL);
         this.handleTeamKill(s1, s, ObjectiveCriteria.KILLED_BY_TEAM);
         CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, p_9050_, p_9052_);
      }
   }

   private void handleTeamKill(String p_9125_, String p_9126_, ObjectiveCriteria[] p_9127_) {
      PlayerTeam playerteam = this.getScoreboard().getPlayersTeam(p_9126_);
      if (playerteam != null) {
         int i = playerteam.getColor().getId();
         if (i >= 0 && i < p_9127_.length) {
            // CraftBukkit - Get our scores instead
            this.level.getCraftServer().getScoreboardManager().getScoreboardScores(p_9127_[i], p_9125_, Score::increment);
         }
      }

   }

   public boolean hurt(DamageSource p_9037_, float p_9038_) {
      if (this.isInvulnerableTo(p_9037_)) {
         return false;
      } else {
         boolean flag = this.server.isDedicatedServer() && this.isPvpAllowed() && p_9037_.is(DamageTypeTags.IS_FALL);
         if (!flag && this.spawnInvulnerableTime > 0 && !p_9037_.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
         } else {
            Entity entity = p_9037_.getEntity();
            if (entity instanceof Player) {
               Player player = (Player)entity;
               if (!this.canHarmPlayer(player)) {
                  return false;
               }
            }

            if (entity instanceof AbstractArrow) {
               AbstractArrow abstractarrow = (AbstractArrow)entity;
               Entity entity1 = abstractarrow.getOwner();
               if (entity1 instanceof Player) {
                  Player player1 = (Player)entity1;
                  if (!this.canHarmPlayer(player1)) {
                     return false;
                  }
               }
            }

            return super.hurt(p_9037_, p_9038_);
         }
      }
   }

   public boolean canHarmPlayer(Player p_9064_) {
      return !this.isPvpAllowed() ? false : super.canHarmPlayer(p_9064_);
   }

   private boolean isPvpAllowed() {
      return this.level.pvpMode;
   }

   @Nullable
   protected PortalInfo findDimensionEntryPoint(ServerLevel p_8998_) {
      PortalInfo portalinfo = super.findDimensionEntryPoint(p_8998_);
      p_8998_ = (portalinfo == null) ? p_8998_ : portalinfo.world; // CraftBukkit
      if (portalinfo != null && this.level.getTypeKey() == LevelStem.OVERWORLD && p_8998_ != null && p_8998_.getTypeKey() == LevelStem.END) { // CraftBukkit
         Vec3 vec3 = portalinfo.pos.add(0.0D, -1.0D, 0.0D);
         PortalInfo newInfo = new PortalInfo(vec3, Vec3.ZERO, 90.0F, 0.0F); // CraftBukkit
         newInfo.setWorld(p_8998_);
         newInfo.setPortalEventInfo(portalinfo.portalEventInfo);
         return newInfo; // CraftBukkit
      } else {
         return portalinfo;
      }
   }

   // Mohsit start
   public AtomicReference<TeleportCause> changeDimensionCause = new AtomicReference<>(TeleportCause.UNKNOWN);

   public Entity changeDimensionCB(ServerLevel serverlevel, TeleportCause teleportCause) {
      changeDimensionCause.set(teleportCause);
      return changeDimension(serverlevel);
   }

   @Nullable
   public Entity changeDimension(ServerLevel p_9180_) {
      return changeDimension(p_9180_, p_9180_.getPortalForcer());
   }


   public PortalInfo portalinfoBukkit = null;
   public Location exit = null;

   @Nullable
   public Entity changeDimension(ServerLevel p_9180_, ITeleporter teleporter) {
      if (!ForgeHooks.onTravelToDimension(this, p_9180_.dimension())) return null;
      if (this.isSleeping()) return this; // CraftBukkit - SPIGOT-3154
      portalinfoBukkit = teleporter.getPortalInfo(this, p_9180_, this::findDimensionEntryPoint);
      if (portalinfoBukkit == null)
         return null;
      {
         // CraftBukkit - start
         Location enter = this.getBukkitEntity().getLocation();
         exit = (server == null) ? null : new Location(p_9180_.getWorld(), portalinfoBukkit.pos.x, portalinfoBukkit.pos.y, portalinfoBukkit.pos.z, portalinfoBukkit.yRot, portalinfoBukkit.xRot);
         final PlayerTeleportEvent tpEvent = new PlayerTeleportEvent(this.getBukkitEntity(), enter, exit, changeDimensionCause.getAndSet(TeleportCause.UNKNOWN));
         Bukkit.getServer().getPluginManager().callEvent(tpEvent);
         if (tpEvent.isCancelled() || tpEvent.getTo() == null) {
            return null;
         }
      }
      // CraftBukkit end
      this.isChangingDimension = true;
      ServerLevel serverlevel = this.serverLevel();
      ResourceKey<LevelStem> resourcekey = serverlevel.getTypeKey();
      if (resourcekey == LevelStem.END && serverlevel != null && p_9180_.getTypeKey() == LevelStem.OVERWORLD && teleporter.isVanilla()) { //Forge: Fix non-vanilla teleporters triggering end credits
         this.isChangingDimension = true; // CraftBukkit - Moved down from above
         this.unRide();
         this.serverLevel().removePlayerImmediately(this, RemovalReason.CHANGED_DIMENSION);
         if (!this.wonGame) {
            this.wonGame = true;
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, this.seenCredits ? 0.0F : 1.0F));
            this.seenCredits = true;
         }
         return this;
      } else {
         LevelData leveldata = p_9180_.getLevelData();
         this.connection.send(new ClientboundRespawnPacket(p_9180_.dimensionTypeId(), p_9180_.dimension(), BiomeManager.obfuscateSeed(p_9180_.getSeed()), this.gameMode.getGameModeForPlayer(), this.gameMode.getPreviousGameModeForPlayer(), p_9180_.isDebug(), p_9180_.isFlat(), (byte)3, this.getLastDeathLocation(), this.getPortalCooldown()));
         this.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
         PlayerList playerlist = this.server.getPlayerList();
         playerlist.sendPlayerPermissionLevel(this);
         serverlevel.removePlayerImmediately(this, RemovalReason.CHANGED_DIMENSION);
         this.revive();
         PortalInfo portalinfo = portalinfoBukkit;
         if (portalinfo != null) {
            Entity e = teleporter.placeEntity(this, serverlevel, p_9180_, this.getYRot(), spawnPortal -> {//Forge: Start vanilla logic
            serverlevel.getProfiler().push("moving");
            if (resourcekey == LevelStem.OVERWORLD && p_9180_.getTypeKey() == LevelStem.NETHER) { // CraftBukkit
               this.enteredNetherPosition = this.position();
            } else if (spawnPortal && p_9180_.getTypeKey() == LevelStem.END) { // CraftBukkit
               this.createEndPlatform(p_9180_, BlockPos.containing(portalinfo.pos));
            }

            serverlevel.getProfiler().pop();
            serverlevel.getProfiler().push("placing");
            this.setServerLevel(p_9180_);
            this.connection.teleport(exit); // CraftBukkit - use internal teleport without event
            this.connection.resetPosition();
            p_9180_.addDuringPortalTeleport(this);
            serverlevel.getProfiler().pop();
            this.triggerDimensionChangeTriggers(serverlevel);
            return this;//forge: this is part of the ITeleporter patch
            });//Forge: End vanilla logic
            if (e != this) throw new IllegalArgumentException(String.format(Locale.ENGLISH, "Teleporter %s returned not the player entity but instead %s, expected PlayerEntity %s", teleporter, e, this));
            this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
            playerlist.sendLevelInfo(this, p_9180_);
            playerlist.sendAllPlayerInfo(this);

            for(MobEffectInstance mobeffectinstance : this.getActiveEffects()) {
               this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), mobeffectinstance));
            }

            if (teleporter.playTeleportSound(this, serverlevel, p_9180_))
            this.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
            this.lastSentExp = -1;
            this.lastSentHealth = -1.0F;
            this.lastSentFood = -1;
            ForgeEventFactory.firePlayerChangedDimensionEvent(this, serverlevel.dimension(), p_9180_.dimension());
            // CraftBukkit start
            PlayerChangedWorldEvent changeEvent = new PlayerChangedWorldEvent(this.getBukkitEntity(), serverlevel.getWorld());
            this.level.getCraftServer().getPluginManager().callEvent(changeEvent);
            // CraftBukkit end
         }

         return this;
      }
   }

   // CraftBukkit start
   @Override
   protected CraftPortalEvent callPortalEvent(Entity entity, ServerLevel exitServerLevel, PositionImpl exitPosition, TeleportCause cause, int searchRadius, int creationRadius) {
      Location enter = this.getBukkitEntity().getLocation();
      Location exit = new Location(exitServerLevel.getWorld(), exitPosition.x(), exitPosition.y(), exitPosition.z(), getYRot(), getXRot());
      PlayerPortalEvent event = new PlayerPortalEvent(this.getBukkitEntity(), enter, exit, cause, searchRadius, true, creationRadius);
      Bukkit.getServer().getPluginManager().callEvent(event);
      if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) {
         return null;
      }
      return new CraftPortalEvent(event);
   }
   // CraftBukkit end

   private void createEndPlatform(ServerLevel p_9007_, BlockPos p_9008_) {
      MutableBlockPos blockpos$mutableblockpos = p_9008_.mutable();
      BlockStateListPopulator blockList = new BlockStateListPopulator(p_9007_); // CraftBukkit
      for(int i = -2; i <= 2; ++i) {
         for(int j = -2; j <= 2; ++j) {
            for(int k = -1; k < 3; ++k) {
               BlockState blockstate = k == -1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();
               blockList.setBlock(blockpos$mutableblockpos.set(p_9008_).move(j, k, i), blockstate, 3); // CraftBukkit
            }
         }
      }
      // CraftBukkit start - call portal event
      PortalCreateEvent portalEvent = new PortalCreateEvent((List<org.bukkit.block.BlockState>) (List) blockList.getList(), p_9007_.getWorld(), this.getBukkitEntity(), CreateReason.END_PLATFORM);
      p_9007_.getCraftServer().getPluginManager().callEvent(portalEvent);
      if (!portalEvent.isCancelled()) {
         blockList.updateList();
      }
      // CraftBukkit end

   }

   protected Optional<FoundRectangle> getExitPortal(ServerLevel p_184131_, BlockPos p_184132_, boolean p_184133_, WorldBorder p_184134_) {
      Optional<FoundRectangle> optional = super.getExitPortal(p_184131_, p_184132_, p_184133_, p_184134_);
      if (optional.isPresent()) {
         return optional;
      } else {
         Axis direction$axis = this.level.getBlockState(this.portalEntrancePos).getOptionalValue(NetherPortalBlock.AXIS).orElse(Axis.X);
         Optional<FoundRectangle> optional1 = p_184131_.getPortalForcer().createPortal(p_184132_, direction$axis);
         if (!optional1.isPresent()) {
            LOGGER.error("Unable to create a portal, likely target out of worldborder");
         }

         return optional1;
      }
   }

   @Override
   protected Optional<FoundRectangle> getExitPortal(ServerLevel worldserver, BlockPos blockposition, boolean flag, WorldBorder worldborder, int searchRadius, boolean canCreatePortal, int createRadius) {
      Optional<FoundRectangle> optional = super.getExitPortal(worldserver, blockposition, flag, worldborder, searchRadius, canCreatePortal, createRadius);
      if (optional.isPresent() || !canCreatePortal) {
         return optional;
      }
      Axis enumdirection_enumaxis = this.level.getBlockState(this.portalEntrancePos).getOptionalValue(NetherPortalBlock.AXIS).orElse(Axis.X);
      Optional<FoundRectangle> optional1 = worldserver.getPortalForcer().createPortal(blockposition, enumdirection_enumaxis, this, createRadius);
      if (!optional1.isPresent()) {
         //  LOGGER.error("Unable to create a portal, likely target out of worldborder");
      }
      return optional1;
   }

   public void triggerDimensionChangeTriggers(ServerLevel p_9210_) {
      ResourceKey<Level> resourcekey = p_9210_.dimension();
      ResourceKey<Level> resourcekey1 = this.level().dimension();
      CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourcekey, resourcekey1);
      if (resourcekey == Level.NETHER && resourcekey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
         CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
      }

      if (resourcekey1 != Level.NETHER) {
         this.enteredNetherPosition = null;
      }

   }

   public boolean broadcastToPlayer(ServerPlayer p_9014_) {
      if (p_9014_.isSpectator()) {
         return this.getCamera() == this;
      } else {
         return this.isSpectator() ? false : super.broadcastToPlayer(p_9014_);
      }
   }

   public void take(Entity p_9047_, int p_9048_) {
      super.take(p_9047_, p_9048_);
      this.containerMenu.broadcastChanges();
   }

   public Either<BedSleepingProblem, Unit> bedResult; // Mohist
   public Either<BedSleepingProblem, Unit> startSleepInBed(BlockPos p_9115_) {
      bedResult = null;
      Optional<BlockPos> optAt = Optional.of(p_9115_);
      BedSleepingProblem ret = ForgeEventFactory.onPlayerSleepInBed(this, optAt);
      if (ret != null) bedResult = Either.left(ret);
      Direction direction = this.level().getBlockState(p_9115_).getValue(HorizontalDirectionalBlock.FACING);
      if (!this.isSleeping() && this.isAlive()) {
         if (!this.level().dimensionType().natural() || !this.level().dimensionType().bedWorks()) {
            bedResult = Either.left(BedSleepingProblem.NOT_POSSIBLE_HERE);
         } else if (!this.bedInRange(p_9115_, direction)) {
            bedResult = Either.left(BedSleepingProblem.TOO_FAR_AWAY);
         } else if (this.bedBlocked(p_9115_, direction)) {
            bedResult = Either.left(BedSleepingProblem.OBSTRUCTED);
         } else {
            this.cause = Cause.BED; // Mohist
            this.setRespawnPosition(this.level().dimension(), p_9115_, this.getYRot(), false, true);
            if (!ForgeEventFactory.fireSleepingTimeCheck(this, optAt)) {
               bedResult = Either.left(BedSleepingProblem.NOT_POSSIBLE_NOW);
            } else {
               if (!this.isCreative()) {
                  double d0 = 8.0D;
                  double d1 = 5.0D;
                  Vec3 vec3 = Vec3.atBottomCenterOf(p_9115_);
                  List<Monster> list = this.level().getEntitiesOfClass(Monster.class, new AABB(vec3.x() - 8.0D, vec3.y() - 5.0D, vec3.z() - 8.0D, vec3.x() + 8.0D, vec3.y() + 5.0D, vec3.z() + 8.0D), (p_9062_) -> {
                     return p_9062_.isPreventingPlayerRest(this);
                  });
                  if (!list.isEmpty()) {
                     bedResult = Either.left(BedSleepingProblem.NOT_SAFE);
                  }
               }

               if (bedResult == null) {
                  bedResult = Either.right(Unit.INSTANCE);
               }
            }
         }
      } else {
         bedResult = Either.left(BedSleepingProblem.OTHER_PROBLEM);
      }

      if (bedResult.left().orElse(null) == BedSleepingProblem.OTHER_PROBLEM) {
         return bedResult; // return immediately if the result is not bypassable by plugins
      }

      if (startSleepInBed_force.getAndSet(false)) {
         bedResult = Either.right(Unit.INSTANCE);
      }

      bedResult = CraftEventFactory.callPlayerBedEnterEvent(this, p_9115_, bedResult);
      if (bedResult.left().isPresent()) {
         return bedResult;
      }


      Either<BedSleepingProblem, Unit> either = super.startSleepInBed(p_9115_).ifRight((unit) -> {
         this.awardStat(Stats.SLEEP_IN_BED);
         CriteriaTriggers.SLEPT_IN_BED.trigger(this);
      });
      if (!this.serverLevel().canSleepThroughNights()) {
         this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
      }

      ((ServerLevel) this.level).updateSleepingPlayerList();
      return either;
   }

   public void startSleeping(BlockPos p_9190_) {
      this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
      super.startSleeping(p_9190_);
   }

   private boolean bedInRange(BlockPos p_9117_, Direction p_9118_) {
      if (p_9118_ == null) return false;
      return this.isReachableBedBlock(p_9117_) || this.isReachableBedBlock(p_9117_.relative(p_9118_.getOpposite()));
   }

   private boolean isReachableBedBlock(BlockPos p_9223_) {
      Vec3 vec3 = Vec3.atBottomCenterOf(p_9223_);
      return Math.abs(this.getX() - vec3.x()) <= 3.0D && Math.abs(this.getY() - vec3.y()) <= 2.0D && Math.abs(this.getZ() - vec3.z()) <= 3.0D;
   }

   private boolean bedBlocked(BlockPos p_9192_, Direction p_9193_) {
      BlockPos blockpos = p_9192_.above();
      return !this.freeAt(blockpos) || !this.freeAt(blockpos.relative(p_9193_.getOpposite()));
   }

   public void stopSleepInBed(boolean p_9165_, boolean p_9166_) {
      if (!this.isSleeping()) return; // CraftBukkit - Can't leave bed if not in one!
      // CraftBukkit start - fire PlayerBedLeaveEvent
      CraftPlayer player = this.getBukkitEntity();
      BlockPos bedPosition = this.getSleepingPos().orElse(null);

      Block bed;
      if (bedPosition != null) {
         bed = this.level.getWorld().getBlockAt(bedPosition.getX(), bedPosition.getY(), bedPosition.getZ());
      } else {
         bed = this.level.getWorld().getBlockAt(player.getLocation());
      }

      PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(player, bed, true);
      this.level.getCraftServer().getPluginManager().callEvent(event);
      if (event.isCancelled()) {
         return;
      }
      // CraftBukkit end
      if (this.isSleeping()) {
         this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(this, 2));
      }

      super.stopSleepInBed(p_9165_, p_9166_);
      if (this.connection != null) {
         this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), TeleportCause.EXIT_BED); // CraftBukkit
      }

   }

   public void dismountTo(double p_143389_, double p_143390_, double p_143391_) {
      this.removeVehicle();
      this.setPos(p_143389_, p_143390_, p_143391_);
   }

   public boolean isInvulnerableTo(DamageSource p_9182_) {
      return super.isInvulnerableTo(p_9182_) || this.isChangingDimension();
   }

   protected void checkFallDamage(double p_8976_, boolean p_8977_, BlockState p_8978_, BlockPos p_8979_) {
   }

   protected void onChangedBlock(BlockPos p_9206_) {
      if (!this.isSpectator()) {
         super.onChangedBlock(p_9206_);
      }

   }

   public void doCheckFallDamage(double p_289676_, double p_289671_, double p_289665_, boolean p_289696_) {
      if (!this.touchingUnloadedChunk()) {
         this.checkSupportingBlock(p_289696_, new Vec3(p_289676_, p_289671_, p_289665_));
         BlockPos blockpos = this.getOnPosLegacy();
         super.checkFallDamage(p_289671_, p_289696_, this.level().getBlockState(blockpos), blockpos);
      }
   }

   public void openTextEdit(SignBlockEntity p_277909_, boolean p_277495_) {
      this.connection.send(new ClientboundBlockUpdatePacket(this.level(), p_277909_.getBlockPos()));
      this.connection.send(new ClientboundOpenSignEditorPacket(p_277909_.getBlockPos(), p_277495_));
   }

   public void nextContainerCounter() {
      this.containerCounter = this.containerCounter % 100 + 1;
   }

   public int nextContainerCounterInt() {
      this.containerCounter = this.containerCounter % 100 + 1;
      return containerCounter; // CraftBukkit
   }

   public OptionalInt openMenu(@Nullable MenuProvider p_9033_) {
      if (p_9033_ == null) {
         return OptionalInt.empty();
      } else {
         // CraftBukkit start - SPIGOT-6552: Handle inventory closing in CraftEventFactory#callInventoryOpenEvent(...)
         /*
         if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
         }
         */
         // CraftBukkit end

         this.nextContainerCounter();
         AbstractContainerMenu abstractcontainermenu = p_9033_.createMenu(this.containerCounter, this.getInventory(), this);

         // CraftBukkit start - Inventory open hook
         if (abstractcontainermenu != null) {
            abstractcontainermenu.setTitle(p_9033_.getDisplayName());

            // Mohist start - Custom Container compatible with mods
            if (abstractcontainermenu.getBukkitView() == null) {
               org.bukkit.inventory.Inventory inventory = new CraftInventory(new MohistModsInventory(abstractcontainermenu, this));
               inventory.getType().setMods(true);
               abstractcontainermenu.bukkitView = new CraftInventoryView(this.getBukkitEntity(), inventory, abstractcontainermenu);
            }
            // Mohist end

            boolean cancelled = false;
            // Mohist start - Moved from CraftEventFactory#callInventoryOpenEvent(...) to fix mixin issues
            if (this.containerMenu != this.inventoryMenu) {
               this.closeContainer();
               CraftEventFactory.alreadyProcessed = true;
            }
            // Mohist end
            abstractcontainermenu = CraftEventFactory.callInventoryOpenEvent(this, abstractcontainermenu, cancelled);
            if (abstractcontainermenu == null && !cancelled) { // Let pre-cancelled events fall through
               // SPIGOT-5263 - close chest if cancelled
               if (p_9033_ instanceof Container) {
                  ((Container) p_9033_).stopOpen(this);
               } else if (p_9033_ instanceof DoubleInventory) {
                  // SPIGOT-5355 - double chests too :(
                  ((DoubleInventory) p_9033_).inventorylargechest.stopOpen(this);
               }
               return OptionalInt.empty();
            }
         }
         // CraftBukkit end

         if (abstractcontainermenu == null) {
            if (this.isSpectator()) {
               this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
            }

            return OptionalInt.empty();
         } else {
            this.containerMenu = abstractcontainermenu; // CraftBukkit
            this.connection.send(new ClientboundOpenScreenPacket(abstractcontainermenu.containerId, abstractcontainermenu.getType(), abstractcontainermenu.getTitle()));
            this.initMenu(abstractcontainermenu);
            MinecraftForge.EVENT_BUS.post(new Open(this, this.containerMenu));
            return OptionalInt.of(this.containerCounter);
         }
      }
   }

   public void sendMerchantOffers(int p_8988_, MerchantOffers p_8989_, int p_8990_, int p_8991_, boolean p_8992_, boolean p_8993_) {
      this.connection.send(new ClientboundMerchantOffersPacket(p_8988_, p_8989_, p_8990_, p_8991_, p_8992_, p_8993_));
   }

   public void openHorseInventory(AbstractHorse p_9059_, Container p_9060_) {
      // CraftBukkit start - Inventory open hook
      this.nextContainerCounter();
      AbstractContainerMenu container = new HorseInventoryMenu(this.containerCounter, this.getInventory(), p_9060_, p_9059_);
      container.setTitle(p_9059_.getDisplayName());
      container = CraftEventFactory.callInventoryOpenEvent(this, container);

      if (container == null) {
         p_9060_.stopOpen(this);
         return;
      }
      // CraftBukkit end
      if (this.containerMenu != this.inventoryMenu) {
         this.closeContainer();
      }

      // this.nextContainerCounter(); // CraftBukkit - moved up
      this.connection.send(new ClientboundHorseScreenOpenPacket(this.containerCounter, p_9060_.getContainerSize(), p_9059_.getId()));
      this.containerMenu = container; // CraftBukkit
      this.initMenu(this.containerMenu);
      MinecraftForge.EVENT_BUS.post(new Open(this, this.containerMenu));
   }

   public void openItemGui(ItemStack p_9082_, InteractionHand p_9083_) {
      if (p_9082_.is(Items.WRITTEN_BOOK)) {
         if (WrittenBookItem.resolveBookComponents(p_9082_, this.createCommandSourceStack(), this)) {
            this.containerMenu.broadcastChanges();
         }

         this.connection.send(new ClientboundOpenBookPacket(p_9083_));
      }

   }

   public void openCommandBlock(CommandBlockEntity p_9099_) {
      this.connection.send(ClientboundBlockEntityDataPacket.create(p_9099_, BlockEntity::saveWithoutMetadata));
   }

   public void closeContainer() {
      this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
      this.doCloseContainer();
   }

   public void doCloseContainer() {
      this.containerMenu.removed(this);
      this.inventoryMenu.transferState(this.containerMenu);
      MinecraftForge.EVENT_BUS.post(new Close(this, this.containerMenu));
      this.containerMenu = this.inventoryMenu;
   }

   public void setPlayerInput(float p_8981_, float p_8982_, boolean p_8983_, boolean p_8984_) {
      if (this.isPassenger()) {
         if (p_8981_ >= -1.0F && p_8981_ <= 1.0F) {
            this.xxa = p_8981_;
         }

         if (p_8982_ >= -1.0F && p_8982_ <= 1.0F) {
            this.zza = p_8982_;
         }

         this.jumping = p_8983_;
         // CraftBukkit start
         if (p_8984_ != this.isShiftKeyDown()) {
            PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getBukkitEntity(), p_8984_);
            this.server.server.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
               return;
            }
         }
         // CraftBukkit end
         this.setShiftKeyDown(p_8984_);
      }

   }

   public void awardStat(Stat<?> p_9026_, int p_9027_) {
      this.stats.increment(this, p_9026_, p_9027_);
      this.getScoreboard().forAllObjectives(p_9026_, this.getScoreboardName(), (p_8996_) -> {
         p_8996_.add(p_9027_);
      });
   }

   public void resetStat(Stat<?> p_9024_) {
      this.stats.setValue(this, p_9024_, 0);
      this.getScoreboard().forAllObjectives(p_9024_, this.getScoreboardName(), Score::reset);
   }

   public int awardRecipes(Collection<Recipe<?>> p_9129_) {
      return this.recipeBook.addRecipes(p_9129_, this);
   }

   public void triggerRecipeCrafted(Recipe<?> p_283176_, List<ItemStack> p_282336_) {
      CriteriaTriggers.RECIPE_CRAFTED.trigger(this, p_283176_.getId(), p_282336_);
   }

   public void awardRecipesByKey(ResourceLocation[] p_9168_) {
      List<Recipe<?>> list = Lists.newArrayList();

      for(ResourceLocation resourcelocation : p_9168_) {
         this.server.getRecipeManager().byKey(resourcelocation).ifPresent(list::add);
      }

      this.awardRecipes(list);
   }

   public int resetRecipes(Collection<Recipe<?>> p_9195_) {
      return this.recipeBook.removeRecipes(p_9195_, this);
   }

   public void giveExperiencePoints(int p_9208_) {
      super.giveExperiencePoints(p_9208_);
      this.lastSentExp = -1;
   }

   public void disconnect() {
      this.disconnected = true;
      this.ejectPassengers();
      if (this.isSleeping()) {
         this.stopSleepInBed(true, false);
      }

   }

   public boolean hasDisconnected() {
      return this.disconnected;
   }

   public void resetSentInfo() {
      this.lastSentHealth = -1.0E8F;
      this.lastSentExp = -1; // CraftBukkit - Added to reset
   }

   public void displayClientMessage(Component p_9154_, boolean p_9155_) {
      this.sendSystemMessage(p_9154_, p_9155_);
   }

   protected void completeUsingItem() {
      if (!this.useItem.isEmpty() && this.isUsingItem()) {
         this.connection.send(new ClientboundEntityEventPacket(this, (byte)9));
         super.completeUsingItem();
      }

   }

   public void lookAt(Anchor p_9112_, Vec3 p_9113_) {
      super.lookAt(p_9112_, p_9113_);
      this.connection.send(new ClientboundPlayerLookAtPacket(p_9112_, p_9113_.x, p_9113_.y, p_9113_.z));
   }

   public void lookAt(Anchor p_9108_, Entity p_9109_, Anchor p_9110_) {
      Vec3 vec3 = p_9110_.apply(p_9109_);
      super.lookAt(p_9108_, vec3);
      this.connection.send(new ClientboundPlayerLookAtPacket(p_9108_, p_9109_, p_9110_));
   }

   public void restoreFrom(ServerPlayer p_9016_, boolean p_9017_) {
      this.wardenSpawnTracker = p_9016_.wardenSpawnTracker;
      this.textFilteringEnabled = p_9016_.textFilteringEnabled;
      this.chatSession = p_9016_.chatSession;
      this.gameMode.setGameModeForPlayer(p_9016_.gameMode.getGameModeForPlayer(), p_9016_.gameMode.getPreviousGameModeForPlayer());
      this.onUpdateAbilities();
      if (p_9017_) {
         this.getInventory().replaceWith(p_9016_.getInventory());
         this.setHealth(p_9016_.getHealth());
         this.foodData = p_9016_.foodData;
         this.experienceLevel = p_9016_.experienceLevel;
         this.totalExperience = p_9016_.totalExperience;
         this.experienceProgress = p_9016_.experienceProgress;
         this.setScore(p_9016_.getScore());
         this.portalEntrancePos = p_9016_.portalEntrancePos;
      } else if (KeepInventory.inventory(p_9016_) || p_9016_.isSpectator()) {
         this.getInventory().replaceWith(p_9016_.getInventory());
         if (KeepInventory.exp(p_9016_) || this.keepLevel) {
            this.experienceLevel = p_9016_.experienceLevel;
            this.totalExperience = p_9016_.totalExperience;
            this.experienceProgress = p_9016_.experienceProgress;
         }
         this.setScore(p_9016_.getScore());
      }

      this.enchantmentSeed = p_9016_.enchantmentSeed;
      this.enderChestInventory = p_9016_.enderChestInventory;
      this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, p_9016_.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
      this.lastSentExp = -1;
      this.lastSentHealth = -1.0F;
      this.lastSentFood = -1;
      this.recipeBook.copyOverData(p_9016_.recipeBook);
      this.seenCredits = p_9016_.seenCredits;
      this.enteredNetherPosition = p_9016_.enteredNetherPosition;
      this.setShoulderEntityLeft(p_9016_.getShoulderEntityLeft());
      this.setShoulderEntityRight(p_9016_.getShoulderEntityRight());
      this.setLastDeathLocation(p_9016_.getLastDeathLocation());

      //Copy over a section of the Entity Data from the old player.
      //Allows mods to specify data that persists after players respawn.
      CompoundTag old = p_9016_.getPersistentData();
      if (old.contains(PERSISTED_NBT_TAG))
          getPersistentData().put(PERSISTED_NBT_TAG, old.get(PERSISTED_NBT_TAG));
      ForgeEventFactory.onPlayerClone(this, p_9016_, !p_9017_);
      this.tabListHeader = p_9016_.tabListHeader;
      this.tabListFooter = p_9016_.tabListFooter;
      this.language = p_9016_.language; // Mohist - Language not synchronized when respawn
   }

   protected void onEffectAdded(MobEffectInstance p_143393_, @Nullable Entity p_143394_) {
      super.onEffectAdded(p_143393_, p_143394_);
      this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), p_143393_));
      if (p_143393_.getEffect() == MobEffects.LEVITATION) {
         this.levitationStartTime = this.tickCount;
         this.levitationStartPos = this.position();
      }

      CriteriaTriggers.EFFECTS_CHANGED.trigger(this, p_143394_);
   }

   protected void onEffectUpdated(MobEffectInstance p_143396_, boolean p_143397_, @Nullable Entity p_143398_) {
      super.onEffectUpdated(p_143396_, p_143397_, p_143398_);
      this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), p_143396_));
      CriteriaTriggers.EFFECTS_CHANGED.trigger(this, p_143398_);
   }

   protected void onEffectRemoved(MobEffectInstance p_9184_) {
      super.onEffectRemoved(p_9184_);
      this.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), p_9184_.getEffect()));
      if (p_9184_.getEffect() == MobEffects.LEVITATION) {
         this.levitationStartPos = null;
      }

      CriteriaTriggers.EFFECTS_CHANGED.trigger(this, (Entity)null);
   }

   public void teleportTo(double p_8969_, double p_8970_, double p_8971_) {
      this.connection.teleport(p_8969_, p_8970_, p_8971_, this.getYRot(), this.getXRot(), RelativeMovement.ROTATION);
   }

   public void teleportRelative(double p_251611_, double p_248861_, double p_252266_) {
      this.connection.teleport(this.getX() + p_251611_, this.getY() + p_248861_, this.getZ() + p_252266_, this.getYRot(), this.getXRot(), RelativeMovement.ALL);
   }

   // Mohist start
   public AtomicReference<TeleportCause> teleportToS$cause = new AtomicReference<>(TeleportCause.UNKNOWN);

   public boolean teleportTo(ServerLevel p_265564_, double p_265424_, double p_265680_, double p_265312_, Set<RelativeMovement> p_265192_, float p_265059_, float p_265266_) {
      ChunkPos chunkpos = new ChunkPos(BlockPos.containing(p_265424_, p_265680_, p_265312_));
      p_265564_.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkpos, 1, this.getId());
      this.stopRiding();
      if (this.isSleeping()) {
         this.stopSleepInBed(true, true);
      }

      if (p_265564_ == this.level()) {
         this.connection.teleport$cause(teleportToS$cause.getAndSet(TeleportCause.UNKNOWN));
         this.connection.teleport(p_265424_, p_265680_, p_265312_, p_265059_, p_265266_, p_265192_);
      } else {
         this.teleportTo$cause.set(teleportToS$cause.getAndSet(TeleportCause.UNKNOWN));
         this.teleportTo(p_265564_, p_265424_, p_265680_, p_265312_, p_265059_, p_265266_);
      }

      this.setYHeadRot(p_265059_);
      return true;
   }

   public boolean teleportTo(ServerLevel pLevel, double pX, double pY, double pZ, Set<RelativeMovement> pRelativeMovements, float pYRot, float pXRot, TeleportCause cause) {
      teleportToS$cause.set(cause);
      return teleportTo(pLevel, pX, pY, pZ, pRelativeMovements, pYRot, pXRot);
   }
   // Mohist end

   public void moveTo(double p_9171_, double p_9172_, double p_9173_) {
      super.moveTo(p_9171_, p_9172_, p_9173_);
      this.connection.resetPosition();
   }

   public void crit(Entity p_9045_) {
      this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(p_9045_, 4));
   }

   public void magicCrit(Entity p_9186_) {
      this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(p_9186_, 5));
   }

   public void onUpdateAbilities() {
      if (this.connection != null) {
         this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
         this.updateInvisibilityStatus();
      }
   }

   public ServerLevel serverLevel() {
      return (ServerLevel)this.level();
   }

   public boolean setGameMode(GameType p_143404_) {
      p_143404_ = ForgeHooks.onChangeGameType(this, this.gameMode.getGameModeForPlayer(), p_143404_);
      if (p_143404_ == null) return false;
      if (!this.gameMode.changeGameModeForPlayer(p_143404_)) {
         return false;
      } else {
         this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float)p_143404_.getId()));
         if (p_143404_ == GameType.SPECTATOR) {
            this.removeEntitiesOnShoulder();
            this.stopRiding();
         } else {
            this.setCamera(this);
         }

         this.onUpdateAbilities();
         this.updateEffectVisibility();
         return true;
      }
   }

   public boolean isSpectator() {
      return this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
   }

   public boolean isCreative() {
      return this.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
   }

   public void sendSystemMessage(Component p_215097_) {
      this.sendSystemMessage(p_215097_, false);
   }

   public void sendSystemMessage(Component p_240560_, boolean p_240545_) {
      if (this.acceptsSystemMessages(p_240545_)) {
         if (connection == null) return; // Mohist
         this.connection.send(new ClientboundSystemChatPacket(p_240560_, p_240545_), PacketSendListener.exceptionallySend(() -> {
            if (this.acceptsSystemMessages(false)) {
               int i = 256;
               String s = p_240560_.getString(256);
               Component component = Component.literal(s).withStyle(ChatFormatting.YELLOW);
               return new ClientboundSystemChatPacket(Component.translatable("multiplayer.message_not_delivered", component).withStyle(ChatFormatting.RED), false);
            } else {
               return null;
            }
         }));
      }
   }

   public void sendChatMessage(OutgoingChatMessage p_249852_, boolean p_250110_, Bound p_252108_) {
      if (this.acceptsChatMessages()) {
         p_249852_.sendToPlayer(this, p_250110_, p_252108_);
      }

   }

   public String getIpAddress() {
      SocketAddress socketaddress = this.connection.getRemoteAddress();
      if (socketaddress instanceof InetSocketAddress inetsocketaddress) {
         return InetAddresses.toAddrString(inetsocketaddress.getAddress());
      } else {
         return "<unknown>";
      }
   }
   public void updateOptions(ServerboundClientInformationPacket p_9157_) {
      // CraftBukkit start
      if (getMainArm() != p_9157_.mainHand()) {
         PlayerChangedMainHandEvent event = new PlayerChangedMainHandEvent(getBukkitEntity(), getMainArm() == HumanoidArm.LEFT ? MainHand.LEFT : MainHand.RIGHT);
         this.server.server.getPluginManager().callEvent(event);
      }
      if (!this.language.equals(p_9157_.language())) {
         PlayerLocaleChangeEvent event = new PlayerLocaleChangeEvent(getBukkitEntity(), p_9157_.language());
         this.server.server.getPluginManager().callEvent(event);
      }
      this.clientViewDistance = p_9157_.viewDistance();
      // CraftBukkit end
      this.chatVisibility = p_9157_.chatVisibility();
      this.canChatColor = p_9157_.chatColors();
      this.textFilteringEnabled = p_9157_.textFilteringEnabled();
      this.allowsListing = p_9157_.allowsListing();
      this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte)p_9157_.modelCustomisation());
      this.getEntityData().set(DATA_PLAYER_MAIN_HAND, (byte)(p_9157_.mainHand() == HumanoidArm.LEFT ? 0 : 1));
      this.language = p_9157_.language();
   }

   public boolean canChatInColor() {
      return this.canChatColor;
   }

   public ChatVisiblity getChatVisibility() {
      return this.chatVisibility;
   }

   private boolean acceptsSystemMessages(boolean p_240568_) {
      return this.chatVisibility == ChatVisiblity.HIDDEN ? p_240568_ : true;
   }

   private boolean acceptsChatMessages() {
      return this.chatVisibility == ChatVisiblity.FULL;
   }

   public void sendTexturePack(String p_143409_, String p_143410_, boolean p_143411_, @Nullable Component p_143412_) {
      this.connection.send(new ClientboundResourcePackPacket(p_143409_, p_143410_, p_143411_, p_143412_));
   }

   public void sendServerStatus(ServerStatus p_215110_) {
      this.connection.send(new ClientboundServerDataPacket(p_215110_.description(), p_215110_.favicon().map(Favicon::iconBytes), p_215110_.enforcesSecureChat()));
   }

   protected int getPermissionLevel() {
      return this.server.getProfilePermissions(this.getGameProfile());
   }

   public void resetLastActionTime() {
      this.lastActionTime = Util.getMillis();
   }

   public ServerStatsCounter getStats() {
      return this.stats;
   }

   public ServerRecipeBook getRecipeBook() {
      return this.recipeBook;
   }

   protected void updateInvisibilityStatus() {
      if (this.isSpectator()) {
         this.removeEffectParticles();
         this.setInvisible(true);
      } else {
         super.updateInvisibilityStatus();
      }

   }

   public Entity getCamera() {
      return (Entity)(this.camera == null ? this : this.camera);
   }

   public void setCamera(@Nullable Entity p_9214_) {
      Entity entity = this.getCamera();
      this.camera = (Entity)(p_9214_ == null ? this : p_9214_);
      while (this.camera instanceof PartEntity<?> partEntity) this.camera = partEntity.getParent(); // FORGE: fix MC-46486
      if (entity != this.camera) {
         Level level = this.camera.level();
         if (level instanceof ServerLevel) {
            ServerLevel serverlevel = (ServerLevel)level;
            this.teleportTo(serverlevel, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot());
         }

         if (p_9214_ != null) {
            this.serverLevel().getChunkSource().move(this);
         }

         this.connection.send(new ClientboundSetCameraPacket(this.camera));
         this.connection.resetPosition();
      }

   }

   protected void processPortalCooldown() {
      if (!this.isChangingDimension) {
         super.processPortalCooldown();
      }

   }

   public void attack(Entity p_9220_) {
      if (this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
         this.setCamera(p_9220_);
      } else {
         super.attack(p_9220_);
      }

   }

   public long getLastActionTime() {
      return this.lastActionTime;
   }

   @Nullable
   public Component getTabListDisplayName() {
      if (!this.hasTabListName) {
         this.tabListDisplayName = ForgeEventFactory.getPlayerTabListDisplayName(this);
         this.hasTabListName = true;
      }
      return this.tabListDisplayName;
   }

   public void swing(InteractionHand p_9031_) {
      super.swing(p_9031_);
      this.resetAttackStrengthTicker();
   }

   public boolean isChangingDimension() {
      return this.isChangingDimension;
   }

   public void hasChangedDimension() {
      this.isChangingDimension = false;
   }

   public PlayerAdvancements getAdvancements() {
      return this.advancements;
   }

   // Mohist start
   public AtomicReference<TeleportCause> teleportTo$cause = new AtomicReference<>(TeleportCause.UNKNOWN);
   // CraftBukkit start
   public void teleportTo(ServerLevel p_9000_, double p_9001_, double p_9002_, double p_9003_, float p_9004_, float p_9005_) {
      this.setCamera(this);
      this.stopRiding();
      TeleportCause teleportCause = teleportTo$cause.getAndSet(TeleportCause.UNKNOWN);
      if (teleportCause != TeleportCause.UNKNOWN) {
         this.getBukkitEntity().teleport(new Location(p_9000_.getWorld(), p_9001_, p_9002_, p_9003_, p_9004_, p_9005_), teleportCause);
         return;
      }
      if (p_9000_ == this.level()) {
         this.connection.teleport(p_9001_, p_9002_, p_9003_, p_9004_, p_9005_);
      } else if (ForgeHooks.onTravelToDimension(this, p_9000_.dimension())) {
         ServerLevel serverlevel = this.serverLevel();
         LevelData leveldata = p_9000_.getLevelData();
         this.connection.send(new ClientboundRespawnPacket(p_9000_.dimensionTypeId(), p_9000_.dimension(), BiomeManager.obfuscateSeed(p_9000_.getSeed()), this.gameMode.getGameModeForPlayer(), this.gameMode.getPreviousGameModeForPlayer(), p_9000_.isDebug(), p_9000_.isFlat(), (byte)3, this.getLastDeathLocation(), this.getPortalCooldown()));
         this.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
         this.server.getPlayerList().sendPlayerPermissionLevel(this);
         serverlevel.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
         this.revive();
         this.moveTo(p_9001_, p_9002_, p_9003_, p_9004_, p_9005_);
         this.setServerLevel(p_9000_);
         p_9000_.addDuringCommandTeleport(this);
         this.triggerDimensionChangeTriggers(serverlevel);
         this.connection.teleport(p_9001_, p_9002_, p_9003_, p_9004_, p_9005_);
         this.gameMode.setLevel(p_9000_);
         this.server.getPlayerList().sendLevelInfo(this, p_9000_);
         this.server.getPlayerList().sendAllPlayerInfo(this);
         this.onUpdateAbilities();
         for (MobEffectInstance mobEffect : this.getActiveEffects()) {
            this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), mobEffect));
         }
         WorldManage.changeGameMode(this, p_9000_.getWorld()); // Mohist
         // Don't fire on respawn
         PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(this.getBukkitEntity(), serverlevel.getWorld());
         Bukkit.getPluginManager().callEvent(event);
         ForgeEventFactory.firePlayerChangedDimensionEvent(this, serverlevel.dimension(), p_9000_.dimension());
      }
   }

   public void teleportTo(ServerLevel pNewLevel, double pX, double pY, double pZ, float pYaw, float pPitch, TeleportCause cause) {
      teleportTo$cause.set(cause);
      teleportTo(pNewLevel, pX, pY, pZ, pYaw, pPitch);
   }
   // Mohist end

   @Nullable
   public BlockPos getRespawnPosition() {
      return this.respawnPosition;
   }

   public float getRespawnAngle() {
      return this.respawnAngle;
   }

   public ResourceKey<Level> getRespawnDimension() {
      return this.respawnDimension;
   }

   public boolean isRespawnForced() {
      return this.respawnForced;
   }


   public Cause cause = Cause.UNKNOWN;

   public void setRespawnPosition(ResourceKey<Level> resourcekey, @Nullable BlockPos blockposition, float f, boolean flag, boolean flag1, Cause cause) {
      this.cause = cause;
      this.setRespawnPosition(resourcekey, blockposition, f, flag, flag1);
   }

   public void setRespawnPosition(ResourceKey<Level> p_9159_, @Nullable BlockPos p_9160_, float p_9161_, boolean p_9162_, boolean p_9163_) {
      if (ForgeEventFactory.onPlayerSpawnSet(this, p_9160_ == null ? Level.OVERWORLD : p_9159_, p_9160_, p_9162_)) return;
      ServerLevel newWorld = this.server.getLevel(p_9160_ == null ? Level.OVERWORLD : p_9159_);
      // Mohist start
      if (newWorld == null) {
         newWorld = this.server.overworld();
      }
      // Mohist end
      Location newSpawn = (p_9160_ != null) ? CraftLocation.toBukkit(p_9160_, newWorld.getWorld(), p_9161_, 0) : null;

      PlayerSpawnChangeEvent event = new PlayerSpawnChangeEvent(this.getBukkitEntity(), newSpawn, p_9162_, cause);
      Bukkit.getServer().getPluginManager().callEvent(event);
      if (event.isCancelled()) {
         return;
      }
      newSpawn = event.getNewSpawn();
      p_9162_ = event.isForced();

      if (newSpawn != null) {
         p_9159_ = ((CraftWorld) newSpawn.getWorld()).getHandle().dimension();
         p_9160_ = BlockPos.containing(newSpawn.getX(), newSpawn.getY(), newSpawn.getZ());
         p_9161_ = newSpawn.getYaw();
      } else {
         p_9159_ = Level.OVERWORLD;
         p_9160_ = null;
         p_9161_ = 0.0F;
      }
      // CraftBukkit end
      if (p_9160_ != null) {
         boolean flag = p_9160_.equals(this.respawnPosition) && p_9159_.equals(this.respawnDimension);
         if (p_9163_ && !flag) {
            this.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
         }

         this.respawnPosition = p_9160_;
         this.respawnDimension = p_9159_;
         this.respawnAngle = p_9161_;
         this.respawnForced = p_9162_;
      } else {
         this.respawnPosition = null;
         this.respawnDimension = Level.OVERWORLD;
         this.respawnAngle = 0.0F;
         this.respawnForced = false;
      }

   }

   public void trackChunk(ChunkPos p_184136_, Packet<?> p_184137_) {
      this.connection.send(p_184137_);
      // Paper start
      if(PlayerChunkLoadEvent.getHandlerList().getRegisteredListeners().length > 0){
         new PlayerChunkLoadEvent(this.getBukkitEntity().getWorld().getChunkAt(p_184136_.longKey), this.getBukkitEntity()).callEvent();
      }
      // Paper end
   }

   public void untrackChunk(ChunkPos p_9089_) {
      if (this.isAlive()) {
         this.connection.send(new ClientboundForgetLevelChunkPacket(p_9089_.x, p_9089_.z));
         // Paper start
         if(PlayerChunkUnloadEvent.getHandlerList().getRegisteredListeners().length > 0){
            new PlayerChunkUnloadEvent(this.getBukkitEntity().getWorld().getChunkAt(p_9089_.longKey), this.getBukkitEntity()).callEvent();
         }
         // Paper end
      }

   }

   public SectionPos getLastSectionPos() {
      return this.lastSectionPos;
   }

   public void setLastSectionPos(SectionPos p_9120_) {
      this.lastSectionPos = p_9120_;
   }

   public void playNotifySound(SoundEvent p_9019_, SoundSource p_9020_, float p_9021_, float p_9022_) {
      this.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(p_9019_), p_9020_, this.getX(), this.getY(), this.getZ(), p_9021_, p_9022_, this.random.nextLong()));
   }

   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return new ClientboundAddPlayerPacket(this);
   }

   public ItemEntity drop(ItemStack p_9085_, boolean p_9086_, boolean p_9087_) {
      ItemEntity itementity = super.drop(p_9085_, p_9086_, p_9087_);
      if (itementity == null) {
         return null;
      } else {
         if (captureDrops() != null) captureDrops().add(itementity);
         else
         this.level().addFreshEntity(itementity);
         ItemStack itemstack = itementity.getItem();
         if (p_9087_) {
            if (!itemstack.isEmpty()) {
               this.awardStat(Stats.ITEM_DROPPED.get(itemstack.getItem()), p_9085_.getCount());
            }

            this.awardStat(Stats.DROP);
         }

         return itementity;
      }
   }

   private String language = "en_us";
   /**
    * Returns the language last reported by the player as their local language.
    * Defaults to en_us if the value is unknown.
    */
   public String getLanguage() {
      return this.language;
   }

   private Component tabListHeader = Component.empty();
   private Component tabListFooter = Component.empty();

   public Component getTabListHeader() {
       return this.tabListHeader;
   }

   /**
    * Set the tab list header while preserving the footer.
    *
    * @param header the new header, or {@link Component#empty()} to clear
    */
   public void setTabListHeader(final Component header) {
       this.setTabListHeaderFooter(header, this.tabListFooter);
   }

   public Component getTabListFooter() {
       return this.tabListFooter;
   }

   /**
    * Set the tab list footer while preserving the header.
    *
    * @param footer the new footer, or {@link Component#empty()} to clear
    */
   public void setTabListFooter(final Component footer) {
       this.setTabListHeaderFooter(this.tabListHeader, footer);
   }

   /**
    * Set the tab list header and footer at once.
    *
    * @param header the new header, or {@link Component#empty()} to clear
    * @param footer the new footer, or {@link Component#empty()} to clear
    */
   public void setTabListHeaderFooter(final Component header, final Component footer) {
       if (Objects.equals(header, this.tabListHeader)
           && Objects.equals(footer, this.tabListFooter)) {
           return;
       }

       this.tabListHeader = Objects.requireNonNull(header, "header");
       this.tabListFooter = Objects.requireNonNull(footer, "footer");

       this.connection.send(new ClientboundTabListPacket(header, footer));
   }

   // We need this as tablistDisplayname may be null even if the event was fired.
   private boolean hasTabListName = false;
   public Component tabListDisplayName = null; // Mohist - hook craftbukkit -> Component listName;
   /**
    * Force the name displayed in the tab list to refresh, by firing {@link TabListNameFormat}.
    */
   public void refreshTabListName() {
      Component oldName = this.tabListDisplayName;
      this.tabListDisplayName = ForgeEventFactory.getPlayerTabListDisplayName(this);
      if (!Objects.equals(oldName, this.tabListDisplayName)) {
         this.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, this));
      }
   }

   public TextFilter getTextFilter() {
      return this.textFilter;
   }

   public void setServerLevel(ServerLevel p_284971_) {
      this.setLevel(p_284971_);
      this.gameMode.setLevel(p_284971_);
   }

   @Nullable
   private static GameType readPlayerMode(@Nullable CompoundTag p_143414_, String p_143415_) {
      return p_143414_ != null && p_143414_.contains(p_143415_, 99) ? GameType.byId(p_143414_.getInt(p_143415_)) : null;
   }

   private GameType calculateGameModeForNewPlayer(@Nullable GameType p_143424_) {
      GameType gametype = this.server.getForcedGameType();
      if (gametype != null) {
         return gametype;
      } else {
         return p_143424_ != null ? p_143424_ : this.server.getDefaultGameType();
      }
   }

   public void loadGameTypes(@Nullable CompoundTag p_143428_) {
      this.gameMode.setGameModeForPlayer(this.calculateGameModeForNewPlayer(readPlayerMode(p_143428_, "playerGameType")), readPlayerMode(p_143428_, "previousPlayerGameType"));
   }

   private void storeGameTypes(CompoundTag p_143431_) {
      p_143431_.putInt("playerGameType", this.gameMode.getGameModeForPlayer().getId());
      GameType gametype = this.gameMode.getPreviousGameModeForPlayer();
      if (gametype != null) {
         p_143431_.putInt("previousPlayerGameType", gametype.getId());
      }

   }

   public boolean isTextFilteringEnabled() {
      return this.textFilteringEnabled;
   }

   public boolean shouldFilterMessageTo(ServerPlayer p_143422_) {
      if (p_143422_ == this) {
         return false;
      } else {
         return this.textFilteringEnabled || p_143422_.textFilteringEnabled;
      }
   }

   public boolean mayInteract(Level p_143406_, BlockPos p_143407_) {
      return super.mayInteract(p_143406_, p_143407_) && p_143406_.mayInteract(this, p_143407_);
   }

   protected void updateUsingItem(ItemStack p_143402_) {
      CriteriaTriggers.USING_ITEM.trigger(this, p_143402_);
      super.updateUsingItem(p_143402_);
   }

   public boolean drop(boolean p_182295_) {
      Inventory inventory = this.getInventory();
      ItemStack selected = inventory.getSelected();
      if (selected.isEmpty() || !selected.onDroppedByPlayer(this)) return false;
      if (isUsingItem() && getUsedItemHand() == InteractionHand.MAIN_HAND && (p_182295_ || selected.getCount() == 1)) stopUsingItem(); // Forge: fix MC-231097 on the serverside
      ItemStack itemstack = inventory.removeFromSelected(p_182295_);
      this.containerMenu.findSlot(inventory, inventory.selected).ifPresent((p_287377_) -> {
         this.containerMenu.setRemoteSlot(p_287377_, inventory.getSelected());
      });
      return ForgeHooks.onPlayerTossEvent(this, itemstack, true) != null;
   }

   public boolean allowsListing() {
      return this.allowsListing;
   }

   public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
      return Optional.of(this.wardenSpawnTracker);
   }

   public void onItemPickup(ItemEntity p_215095_) {
      super.onItemPickup(p_215095_);
      Entity entity = p_215095_.getOwner();
      if (entity != null) {
         CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, p_215095_.getItem(), entity);
      }

   }

   public void setChatSession(RemoteChatSession p_254468_) {
      this.chatSession = p_254468_;
   }

   @Nullable
   public RemoteChatSession getChatSession() {
      return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
   }

   public void indicateDamage(double p_270621_, double p_270478_) {
      this.hurtDir = (float)(Mth.atan2(p_270478_, p_270621_) * (double)(180F / (float)Math.PI) - (double)this.getYRot());
      this.connection.send(new ClientboundHurtAnimationPacket(this));
   }

   public boolean startRiding(Entity p_277395_, boolean p_278062_) {
      if (!super.startRiding(p_277395_, p_278062_)) {
         return false;
      } else {
         p_277395_.positionRider(this);
         this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
         if (p_277395_ instanceof LivingEntity) {
            LivingEntity livingentity = (LivingEntity)p_277395_;

            for(MobEffectInstance mobeffectinstance : livingentity.getActiveEffects()) {
               this.connection.send(new ClientboundUpdateMobEffectPacket(p_277395_.getId(), mobeffectinstance));
            }
         }

         return true;
      }
   }

   public void stopRiding() {
      Entity entity = this.getVehicle();
      super.stopRiding();
      if (entity instanceof LivingEntity livingentity) {
         for(MobEffectInstance mobeffectinstance : livingentity.getActiveEffects()) {
            this.connection.send(new ClientboundRemoveMobEffectPacket(entity.getId(), mobeffectinstance.getEffect()));
         }
      }

   }


   // CraftBukkit start - Add per-player time and weather.
   public long timeOffset = 0;
   public boolean relativeTime = true;

   public long getPlayerTime() {
      if (this.relativeTime) {
         // Adds timeOffset to the current server time.
         return this.level.getDayTime() + this.timeOffset;
      } else {
         // Adds timeOffset to the beginning of this day.
         return this.level.getDayTime() - (this.level.getDayTime() % 24000) + this.timeOffset;
      }
   }

   public WeatherType weather = null;

   public WeatherType getPlayerWeather() {
      return this.weather;
   }

   public void setPlayerWeather(WeatherType type, boolean plugin) {
      if (!plugin && this.weather != null) {
         return;
      }

      if (plugin) {
         this.weather = type;
      }

      if (type == WeatherType.DOWNFALL) {
         this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
      } else {
         this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
      }
   }

   private float pluginRainPosition;
   private float pluginRainPositionPrevious;

   public void updateWeather(float oldRain, float newRain, float oldThunder, float newThunder) {
      if (this.weather == null) {
         // Vanilla
         if (oldRain != newRain) {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
         }
      } else {
         // Plugin
         if (pluginRainPositionPrevious != pluginRainPosition) {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, pluginRainPosition));
         }
      }

      if (oldThunder != newThunder) {
         if (weather == WeatherType.DOWNFALL || weather == null) {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
         } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0));
         }
      }
   }

   public void tickWeather() {
      if (this.weather == null) return;

      pluginRainPositionPrevious = pluginRainPosition;
      if (weather == WeatherType.DOWNFALL) {
         pluginRainPosition += 0.01;
      } else {
         pluginRainPosition -= 0.01;
      }

      pluginRainPosition = Mth.clamp(pluginRainPosition, 0.0F, 1.0F);
   }

   public void resetPlayerWeather() {
      this.weather = null;
      this.setPlayerWeather(this.level.getLevelData().isRaining() ? WeatherType.DOWNFALL : WeatherType.CLEAR, false);
   }

   @Override
   public String toString() {
      return super.toString() + "(" + this.getScoreboardName() + " at " + this.getX() + "," + this.getY() + "," + this.getZ() + ")";
   }

   // SPIGOT-1903, MC-98153
   public void forceSetPositionRotation(double x, double y, double z, float yaw, float pitch) {
      this.moveTo(x, y, z, yaw, pitch);
      this.connection.resetPosition();
   }

   @Override
   public boolean isImmobile() {
      return super.isImmobile() || !getBukkitEntity().isOnline();
   }

   @Override
   public Scoreboard getScoreboard() {
      return getBukkitEntity().getScoreboard().getHandle();
   }

   @Override
   public CraftPlayer getBukkitEntity() {
      return (CraftPlayer) super.getBukkitEntity();
   }

   public void reset() {
      float exp = 0;
      if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
         exp = this.experienceProgress;
         this.newTotalExp = this.totalExperience;
         this.newLevel = this.experienceLevel;
      }

      this.setHealth(this.getMaxHealth());
      this.stopUsingItem(); // CraftBukkit - SPIGOT-6682: Clear active item on reset
      this.setRemainingFireTicks(0);
      this.fallDistance = 0;
      this.foodData = new FoodData();
      this.foodData.entityhuman = this;
      this.experienceLevel = this.newLevel;
      this.totalExperience = this.newTotalExp;
      this.experienceProgress = 0;
      this.deathTime = 0;
      this.setArrowCount(0, true); // CraftBukkit - ArrowBodyCountChangeEvent
      this.removeAllEffects(EntityPotionEffectEvent.Cause.DEATH);
      this.effectsDirty = true;
      this.containerMenu = this.inventoryMenu;
      this.lastHurtByPlayer = null;
      this.lastHurtByMob = null;
      this.combatTracker = new CombatTracker(this);
      this.lastSentExp = -1;
      if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
         this.experienceProgress = exp;
      } else {
         this.giveExperiencePoints(this.newExp);
      }
      this.keepLevel = false;
      this.setDeltaMovement(0, 0, 0); // CraftBukkit - SPIGOT-6948: Reset velocity on death
   }

   public void setTabListDisplayName(Component tabListDisplayName) {
      this.tabListDisplayName = tabListDisplayName;
   }
   // CraftBukkit end
}
