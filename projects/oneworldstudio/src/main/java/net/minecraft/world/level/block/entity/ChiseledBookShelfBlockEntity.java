package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.slf4j.Logger;

public class ChiseledBookShelfBlockEntity extends BlockEntity implements Container {
   public static final int MAX_BOOKS_IN_STORAGE = 6;
   private static final Logger LOGGER = LogUtils.getLogger();
   private final NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
   public int lastInteractedSlot = -1;
   // CraftBukkit start - add fields and methods
   public List<HumanEntity> transaction = new java.util.ArrayList<>();
   private int maxStack = 1;

   public ChiseledBookShelfBlockEntity(BlockPos p_249541_, BlockState p_251752_) {
      super(BlockEntityType.CHISELED_BOOKSHELF, p_249541_, p_251752_);
   }

   private void updateState(int p_261806_) {
      if (p_261806_ >= 0 && p_261806_ < 6) {
         this.lastInteractedSlot = p_261806_;
         BlockState blockstate = this.getBlockState();

         for(int i = 0; i < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size(); ++i) {
            boolean flag = !this.getItem(i).isEmpty();
            BooleanProperty booleanproperty = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i);
            blockstate = blockstate.setValue(booleanproperty, Boolean.valueOf(flag));
         }

         Objects.requireNonNull(this.level).setBlock(this.worldPosition, blockstate, 3);
      } else {
         LOGGER.error("Expected slot 0-5, got {}", (int)p_261806_);
      }
   }

   public void load(CompoundTag p_249911_) {
      super.load(p_249911_); // CraftBukkit - SPIGOT-7393: Load super Bukkit data
      this.items.clear();
      ContainerHelper.loadAllItems(p_249911_, this.items);
      this.lastInteractedSlot = p_249911_.getInt("last_interacted_slot");
   }

   protected void saveAdditional(CompoundTag p_251872_) {
      ContainerHelper.saveAllItems(p_251872_, this.items, true);
      p_251872_.putInt("last_interacted_slot", this.lastInteractedSlot);
   }

   public int count() {
      return (int)this.items.stream().filter(Predicate.not(ItemStack::isEmpty)).count();
   }

   public void clearContent() {
      this.items.clear();
   }

   public int getContainerSize() {
      return 6;
   }

   public boolean isEmpty() {
      return this.items.stream().allMatch(ItemStack::isEmpty);
   }

   public ItemStack getItem(int p_256203_) {
      return this.items.get(p_256203_);
   }

   public ItemStack removeItem(int p_255828_, int p_255673_) {
      ItemStack itemstack = Objects.requireNonNullElse(this.items.get(p_255828_), ItemStack.EMPTY);
      this.items.set(p_255828_, ItemStack.EMPTY);
      if (!itemstack.isEmpty()) {
         if (level != null) this.updateState(p_255828_); // CraftBukkit - SPIGOT-7381: check for null world
      }

      return itemstack;
   }

   public ItemStack removeItemNoUpdate(int p_255874_) {
      return this.removeItem(p_255874_, 1);
   }

   public void setItem(int p_256610_, ItemStack p_255789_) {
      if (p_255789_.is(ItemTags.BOOKSHELF_BOOKS)) {
         this.items.set(p_256610_, p_255789_);
         if (level != null) this.updateState(p_256610_); // CraftBukkit - SPIGOT-7381: check for null world
      }

   }

   public boolean canTakeItem(Container p_282172_, int p_281387_, ItemStack p_283257_) {
      return p_282172_.hasAnyMatching((p_281577_) -> {
         if (p_281577_.isEmpty()) {
            return true;
         } else {
            return ItemStack.isSameItemSameTags(p_283257_, p_281577_) && p_281577_.getCount() + p_283257_.getCount() <= Math.min(p_281577_.getMaxStackSize(), p_282172_.getMaxStackSize());
         }
      });
   }

   public int getMaxStackSize() {
      return maxStack; // CraftBukkit
   }

   public boolean stillValid(Player p_256481_) {
      return Container.stillValidBlockEntity(this, p_256481_);
   }

   public boolean canPlaceItem(int p_256567_, ItemStack p_255922_) {
      return p_255922_.is(ItemTags.BOOKSHELF_BOOKS) && this.getItem(p_256567_).isEmpty();
   }

   @Override
   public List<ItemStack> getContents() {
      return this.items;
   }

   @Override
   public void onOpen(CraftHumanEntity who) {
      transaction.add(who);
   }

   @Override
   public void onClose(CraftHumanEntity who) {
      transaction.remove(who);
   }

   @Override
   public List<HumanEntity> getViewers() {
      return transaction;
   }

   @Override
   public void setMaxStackSize(int size) {
      maxStack = size;
   }

   @Override
   public Location getLocation() {
      if (level == null) return null;
      return new org.bukkit.Location(level.getWorld(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
   }
   // CraftBukkit end

   public int getLastInteractedSlot() {
      return this.lastInteractedSlot;
   }

   private net.minecraftforge.common.util.LazyOptional<?> itemHandler = net.minecraftforge.common.util.LazyOptional.of(this::createUnSidedHandler);
   protected net.minecraftforge.items.IItemHandler createUnSidedHandler() {
      return new net.minecraftforge.items.wrapper.InvWrapper(this);
   }

   @Override
   public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side) {
      if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER && !this.remove)
         return itemHandler.cast();
      return super.getCapability(cap, side);
   }

   @Override
   public void invalidateCaps() {
      super.invalidateCaps();
      itemHandler.invalidate();
   }

   @Override
   public void reviveCaps() {
      super.reviveCaps();
      itemHandler = net.minecraftforge.common.util.LazyOptional.of(this::createUnSidedHandler);
   }
}
