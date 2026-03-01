package net.minecraft.world.item.crafting;

import com.oneworldstudiomc.bukkit.inventory.MohistSpecialRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftStonecuttingRecipe;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;

public class StonecutterRecipe extends SingleItemRecipe {
   public StonecutterRecipe(ResourceLocation p_44478_, String p_44479_, Ingredient p_44480_, ItemStack p_44481_) {
      super(RecipeType.STONECUTTING, RecipeSerializer.STONECUTTER, p_44478_, p_44479_, p_44480_, p_44481_);
   }

   public boolean matches(Container p_44483_, Level p_44484_) {
      return this.ingredient.test(p_44483_.getItem(0));
   }

   public ItemStack getToastSymbol() {
      return new ItemStack(Blocks.STONECUTTER);
   }

   // CraftBukkit start
   @Override
   public org.bukkit.inventory.Recipe toBukkitRecipe() {
      if (this.result.isEmpty()) {
         return new MohistSpecialRecipe(this);
      }
      CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

      CraftStonecuttingRecipe recipe = new CraftStonecuttingRecipe(CraftNamespacedKey.fromMinecraft(this.id), result, CraftRecipe.toBukkit(this.ingredient));
      recipe.setGroup(this.group);

      return recipe;
   }
   // CraftBukkit end
}
