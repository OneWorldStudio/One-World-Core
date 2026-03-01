package net.minecraft.world.inventory;

import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;

public class ResultContainer implements Container, RecipeHolder {
   private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(1, ItemStack.EMPTY);
   @Nullable
   private Recipe<?> recipeUsed;

   // CraftBukkit start
   private int maxStack = MAX_STACK;

   public java.util.List<ItemStack> getContents() {
      return this.itemStacks;
   }

   public org.bukkit.inventory.InventoryHolder getOwner() {
      return null; // Result slots don't get an owner
   }

   // Don't need a transaction; the InventoryCrafting keeps track of it for us
   public void onOpen(CraftHumanEntity who) {}
   public void onClose(CraftHumanEntity who) {}
   public java.util.List<HumanEntity> getViewers() {
      return new java.util.ArrayList<HumanEntity>();
   }

   @Override
   public int getMaxStackSize() {
      return maxStack;
   }

   public void setMaxStackSize(int size) {
      maxStack = size;
   }

   @Override
   public Location getLocation() {
      return null;
   }
   // CraftBukkit end
   // Mohist start
   private ItemStack bukkitEventItem;
   public ItemStack getBukkitEventItem() {
      return bukkitEventItem;
   }

   public int getContainerSize() {
      return 1;
   }

   public boolean isEmpty() {
      for(ItemStack itemstack : this.itemStacks) {
         if (!itemstack.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public ItemStack getItem(int p_40147_) {
      return this.itemStacks.get(0);
   }

   public ItemStack removeItem(int p_40149_, int p_40150_) {
      return ContainerHelper.takeItem(this.itemStacks, 0);
   }

   public ItemStack removeItemNoUpdate(int p_40160_) {
      return ContainerHelper.takeItem(this.itemStacks, 0);
   }

   public void setItem(int p_40152_, ItemStack p_40153_) {
      if (p_40152_ == -521) {
         bukkitEventItem = p_40153_;
         return;
      }
      this.itemStacks.set(0, p_40153_);
   }

   public void setChanged() {
   }

   public boolean stillValid(Player p_40155_) {
      return true;
   }

   public void clearContent() {
      this.itemStacks.clear();
   }

   public void setRecipeUsed(@Nullable Recipe<?> p_40157_) {
      this.recipeUsed = p_40157_;
   }

   @Nullable
   public Recipe<?> getRecipeUsed() {
      return this.recipeUsed;
   }
}
