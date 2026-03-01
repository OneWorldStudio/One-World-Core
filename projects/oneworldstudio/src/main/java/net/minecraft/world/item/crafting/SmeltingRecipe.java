package net.minecraft.world.item.crafting;

import com.oneworldstudiomc.bukkit.inventory.MohistSpecialRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftFurnaceRecipe;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.inventory.Recipe;

public class SmeltingRecipe extends AbstractCookingRecipe {
   public SmeltingRecipe(ResourceLocation p_249157_, String p_250200_, CookingBookCategory p_251114_, Ingredient p_250340_, ItemStack p_250306_, float p_249577_, int p_250030_) {
      super(RecipeType.SMELTING, p_249157_, p_250200_, p_251114_, p_250340_, p_250306_, p_249577_, p_250030_);
   }

   public ItemStack getToastSymbol() {
      return new ItemStack(Blocks.FURNACE);
   }

   public RecipeSerializer<?> getSerializer() {
      return RecipeSerializer.SMELTING_RECIPE;
   }

   // CraftBukkit start
   @Override
   public Recipe toBukkitRecipe() {
      if (this.result.isEmpty()) {
         return new MohistSpecialRecipe(this);
      }
      CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

      CraftFurnaceRecipe recipe = new CraftFurnaceRecipe(CraftNamespacedKey.fromMinecraft(this.id), result, CraftRecipe.toBukkit(this.ingredient), this.experience, this.cookingTime);
      recipe.setGroup(this.group);
      recipe.setCategory(CraftRecipe.getCategory(this.category()));

      return recipe;
   }
   // CraftBukkit end
}
