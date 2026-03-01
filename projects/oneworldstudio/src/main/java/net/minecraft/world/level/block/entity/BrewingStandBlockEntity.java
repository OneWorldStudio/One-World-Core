package net.minecraft.world.level.block.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.InventoryHolder;

public class BrewingStandBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
   private static final int INGREDIENT_SLOT = 3;
   private static final int FUEL_SLOT = 4;
   private static final int[] SLOTS_FOR_UP = new int[]{3};
   private static final int[] SLOTS_FOR_DOWN = new int[]{0, 1, 2, 3};
   private static final int[] SLOTS_FOR_SIDES = new int[]{0, 1, 2, 4};
   public static final int FUEL_USES = 20;
   public static final int DATA_BREW_TIME = 0;
   public static final int DATA_FUEL_USES = 1;
   public static final int NUM_DATA_VALUES = 2;
   private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
   public int brewTime;
   private boolean[] lastPotionCount;
   private Item ingredient;
   public int fuel;
   protected final ContainerData dataAccess = new ContainerData() {
      public int get(int p_59038_) {
         switch (p_59038_) {
            case 0:
               return BrewingStandBlockEntity.this.brewTime;
            case 1:
               return BrewingStandBlockEntity.this.fuel;
            default:
               return 0;
         }
      }

      public void set(int p_59040_, int p_59041_) {
         switch (p_59040_) {
            case 0:
               BrewingStandBlockEntity.this.brewTime = p_59041_;
               break;
            case 1:
               BrewingStandBlockEntity.this.fuel = p_59041_;
         }

      }

      public int getCount() {
         return 2;
      }
   };

   // CraftBukkit start - add fields and methods
   private int lastTick = MinecraftServer.currentTick;
   public List<HumanEntity> transaction = new java.util.ArrayList<>();
   private int maxStack = 64;

   public void onOpen(CraftHumanEntity who) {
      transaction.add(who);
   }

   public void onClose(CraftHumanEntity who) {
      transaction.remove(who);
   }

   public List<HumanEntity> getViewers() {
      return transaction;
   }

   public List<ItemStack> getContents() {
      return this.items;
   }

   @Override
   public int getMaxStackSize() {
      return maxStack;
   }

   public void setMaxStackSize(int size) {
      maxStack = size;
   }
   // CraftBukkit end

   public BrewingStandBlockEntity(BlockPos p_155283_, BlockState p_155284_) {
      super(BlockEntityType.BREWING_STAND, p_155283_, p_155284_);
   }

   protected Component getDefaultName() {
      return Component.translatable("container.brewing");
   }

   public int getContainerSize() {
      return this.items.size();
   }

   public boolean isEmpty() {
      for(ItemStack itemstack : this.items) {
         if (!itemstack.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public static void serverTick(Level p_155286_, BlockPos p_155287_, BlockState p_155288_, BrewingStandBlockEntity p_155289_) {
      ItemStack itemstack = p_155289_.items.get(4);
      if (p_155289_.fuel <= 0 && itemstack.is(Items.BLAZE_POWDER)) {
         // CraftBukkit start
         BrewingStandFuelEvent event = new BrewingStandFuelEvent(CraftBlock.at(p_155286_, p_155287_), CraftItemStack.asCraftMirror(itemstack), 20);
         p_155286_.getCraftServer().getPluginManager().callEvent(event);

         if (event.isCancelled()) {
            return;
         }

         p_155289_.fuel = event.getFuelPower();
         if (p_155289_.fuel > 0 && event.isConsuming()) {
            itemstack.shrink(1);
         }
         // CraftBukkit end
         setChanged(p_155286_, p_155287_, p_155288_);
      }

      boolean flag = isBrewable(p_155289_.items);
      boolean flag1 = p_155289_.brewTime > 0;
      ItemStack itemstack1 = p_155289_.items.get(3);

      // CraftBukkit start - Use wall time instead of ticks for brewing
      int elapsedTicks = MinecraftServer.currentTick - p_155289_.lastTick;
      p_155289_.lastTick = MinecraftServer.currentTick;
      if (flag1) {
         p_155289_.brewTime -= elapsedTicks;
         boolean flag2 = p_155289_.brewTime <= 0; // == -> <=
         // CraftBukkit end

         if (flag2 && flag) {
            brewingStandBlock = p_155289_;
            doBrew(p_155286_, p_155287_, p_155289_.items);
            setChanged(p_155286_, p_155287_, p_155288_);
         } else if (!flag || !itemstack1.is(p_155289_.ingredient)) {
            p_155289_.brewTime = 0;
            setChanged(p_155286_, p_155287_, p_155288_);
         }
      } else if (flag && p_155289_.fuel > 0) {
         --p_155289_.fuel;
         // CraftBukkit start
         BrewingStartEvent event = new BrewingStartEvent(CraftBlock.at(p_155286_, p_155287_), CraftItemStack.asCraftMirror(itemstack1), 400);
         p_155286_.getCraftServer().getPluginManager().callEvent(event);
         p_155289_.brewTime = event.getTotalBrewTime(); // 400 -> event.getTotalBrewTime()
         // CraftBukkit end
         p_155289_.ingredient = itemstack1.getItem();
         setChanged(p_155286_, p_155287_, p_155288_);
      }

      boolean[] aboolean = p_155289_.getPotionBits();
      if (!Arrays.equals(aboolean, p_155289_.lastPotionCount)) {
         p_155289_.lastPotionCount = aboolean;
         BlockState blockstate = p_155288_;
         if (!(p_155288_.getBlock() instanceof BrewingStandBlock)) {
            return;
         }

         for(int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; ++i) {
            blockstate = blockstate.setValue(BrewingStandBlock.HAS_BOTTLE[i], Boolean.valueOf(aboolean[i]));
         }

         p_155286_.setBlock(p_155287_, blockstate, 2);
      }

   }

   private boolean[] getPotionBits() {
      boolean[] aboolean = new boolean[3];

      for(int i = 0; i < 3; ++i) {
         if (!this.items.get(i).isEmpty()) {
            aboolean[i] = true;
         }
      }

      return aboolean;
   }

   private static boolean isBrewable(NonNullList<ItemStack> p_155295_) {
      ItemStack itemstack = p_155295_.get(3);
      if (!itemstack.isEmpty()) return net.minecraftforge.common.brewing.BrewingRecipeRegistry.canBrew(p_155295_, itemstack, SLOTS_FOR_SIDES); // divert to VanillaBrewingRegistry
      if (itemstack.isEmpty()) {
         return false;
      } else if (!PotionBrewing.isIngredient(itemstack)) {
         return false;
      } else {
         for(int i = 0; i < 3; ++i) {
            ItemStack itemstack1 = p_155295_.get(i);
            if (!itemstack1.isEmpty() && PotionBrewing.hasMix(itemstack1, itemstack)) {
               return true;
            }
         }

         return false;
      }
   }

   private static BrewingStandBlockEntity brewingStandBlock;
   private static void doBrew(Level p_155291_, BlockPos p_155292_, NonNullList<ItemStack> p_155293_) {
      if (net.minecraftforge.event.ForgeEventFactory.onPotionAttemptBrew(p_155293_)) return;
      ItemStack itemstack = p_155293_.get(3);

      // CraftBukkit start
      List<org.bukkit.inventory.ItemStack> brewResults = new ArrayList<>(3);
      for (int i = 0; i < 3; ++i) {
         var input = p_155293_.get(i);
         var output = BrewingRecipeRegistry.getOutput(input, itemstack);
         brewResults.add(i, CraftItemStack.asCraftMirror(PotionBrewing.mix(itemstack, output)));
      }
      BrewingStandBlockEntity brewingStandBlockEntity = brewingStandBlock;
      InventoryHolder owner = brewingStandBlockEntity == null ? null : brewingStandBlockEntity.getOwner();
      if (owner != null) {
         BrewEvent event = new BrewEvent(CraftBlock.at(p_155291_, p_155292_), (BrewerInventory) owner.getInventory(), brewResults, brewingStandBlockEntity.fuel);
         Bukkit.getPluginManager().callEvent(event);
         if (event.isCancelled()) {
            return;
         } else {
            for (int i = 0; i < 3; ++i) {
               if (i < brewResults.size()) {
                  p_155293_.set(i, CraftItemStack.asNMSCopy(brewResults.get(i)));
               } else {
                  p_155293_.set(i, ItemStack.EMPTY);
               }
            }
         }
      }
      // CraftBukkit end

      // net.minecraftforge.common.brewing.BrewingRecipeRegistry.brewPotions(p_155293_, itemstack, SLOTS_FOR_SIDES); // Mohist remove it
      net.minecraftforge.event.ForgeEventFactory.onPotionBrewed(p_155293_);
      if (itemstack.hasCraftingRemainingItem()) {
         ItemStack itemstack1 = itemstack.getCraftingRemainingItem();
         itemstack.shrink(1);
         if (itemstack.isEmpty()) {
            itemstack = itemstack1;
         } else {
            Containers.dropItemStack(p_155291_, (double)p_155292_.getX(), (double)p_155292_.getY(), (double)p_155292_.getZ(), itemstack1);
         }
      }
      else itemstack.shrink(1);

      p_155293_.set(3, itemstack);
      p_155291_.levelEvent(1035, p_155292_, 0);
   }

   public void load(CompoundTag p_155297_) {
      super.load(p_155297_);
      this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
      ContainerHelper.loadAllItems(p_155297_, this.items);
      this.brewTime = p_155297_.getShort("BrewTime");
      this.fuel = p_155297_.getByte("Fuel");
      this.ingredient = BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(p_155297_.getString("Mohist.ingredient"))); // Mirai - fix brewing stands resetting their brewTime when being unloaded
   }

   protected void saveAdditional(CompoundTag p_187484_) {
      super.saveAdditional(p_187484_);
      p_187484_.putShort("BrewTime", (short)this.brewTime);
      ContainerHelper.saveAllItems(p_187484_, this.items);
      p_187484_.putByte("Fuel", (byte)this.fuel);
      p_187484_.putString("Mohist.ingredient", BuiltInRegistries.ITEM.getKey(this.ingredient).toString()); // Mirai - fix brewing stands resetting their brewTime when being unloaded
   }

   public ItemStack getItem(int p_58985_) {
      return p_58985_ >= 0 && p_58985_ < this.items.size() ? this.items.get(p_58985_) : ItemStack.EMPTY;
   }

   public ItemStack removeItem(int p_58987_, int p_58988_) {
      return ContainerHelper.removeItem(this.items, p_58987_, p_58988_);
   }

   public ItemStack removeItemNoUpdate(int p_59015_) {
      return ContainerHelper.takeItem(this.items, p_59015_);
   }

   public void setItem(int p_58993_, ItemStack p_58994_) {
      if (p_58993_ >= 0 && p_58993_ < this.items.size()) {
         this.items.set(p_58993_, p_58994_);
      }

   }

   public boolean stillValid(Player p_59000_) {
      return Container.stillValidBlockEntity(this, p_59000_);
   }

   public boolean canPlaceItem(int p_59017_, ItemStack p_59018_) {
      if (p_59017_ == 3) {
         return net.minecraftforge.common.brewing.BrewingRecipeRegistry.isValidIngredient(p_59018_);
      } else if (p_59017_ == 4) {
         return p_59018_.is(Items.BLAZE_POWDER);
      } else {
            return net.minecraftforge.common.brewing.BrewingRecipeRegistry.isValidInput(p_59018_) && this.getItem(p_59017_).isEmpty();
      }
   }

   public int[] getSlotsForFace(Direction p_59010_) {
      if (p_59010_ == Direction.UP) {
         return SLOTS_FOR_UP;
      } else {
         return p_59010_ == Direction.DOWN ? SLOTS_FOR_DOWN : SLOTS_FOR_SIDES;
      }
   }

   public boolean canPlaceItemThroughFace(int p_58996_, ItemStack p_58997_, @Nullable Direction p_58998_) {
      return this.canPlaceItem(p_58996_, p_58997_);
   }

   public boolean canTakeItemThroughFace(int p_59020_, ItemStack p_59021_, Direction p_59022_) {
      return p_59020_ == 3 ? p_59021_.is(Items.GLASS_BOTTLE) : true;
   }

   public void clearContent() {
      this.items.clear();
   }

   protected AbstractContainerMenu createMenu(int p_58990_, Inventory p_58991_) {
      return new BrewingStandMenu(p_58990_, p_58991_, this, this.dataAccess);
   }

   net.minecraftforge.common.util.LazyOptional<? extends net.minecraftforge.items.IItemHandler>[] handlers =
           net.minecraftforge.items.wrapper.SidedInvWrapper.create(this, Direction.UP, Direction.DOWN, Direction.NORTH);

   @Override
   public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable Direction facing) {
      if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER && facing != null && !this.remove) {
          return switch (facing) {
              case UP -> handlers[0].cast();
              case DOWN -> handlers[1].cast();
              default -> handlers[2].cast();
          };
      }
      return super.getCapability(capability, facing);
   }

   @Override
   public void invalidateCaps() {
      super.invalidateCaps();
      for (int x = 0; x < handlers.length; x++)
        handlers[x].invalidate();
   }

   @Override
   public void reviveCaps() {
      super.reviveCaps();
      this.handlers = net.minecraftforge.items.wrapper.SidedInvWrapper.create(this, Direction.UP, Direction.DOWN, Direction.NORTH);
   }
}
