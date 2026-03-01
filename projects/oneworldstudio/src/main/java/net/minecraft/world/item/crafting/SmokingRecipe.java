package net.minecraft.world.item.crafting;

import com.oneworldstudiomc.bukkit.inventory.MohistSpecialRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftSmokingRecipe;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.inventory.Recipe;

public class SmokingRecipe extends AbstractCookingRecipe {
   public SmokingRecipe(ResourceLocation p_249711_, String p_249312_, CookingBookCategory p_251017_, Ingredient p_252345_, ItemStack p_250002_, float p_250535_, int p_251222_) {
      super(RecipeType.SMOKING, p_249711_, p_249312_, p_251017_, p_252345_, p_250002_, p_250535_, p_251222_);
   }

   public ItemStack getToastSymbol() {
      return new ItemStack(Blocks.SMOKER);
   }

   public RecipeSerializer<?> getSerializer() {
      return RecipeSerializer.SMOKING_RECIPE;
   }

   // CraftBukkit start
   @Override
   public Recipe toBukkitRecipe() {
      if (this.result.isEmpty()) {
         return new MohistSpecialRecipe(this);
      }
      CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

      CraftSmokingRecipe recipe = new CraftSmokingRecipe(CraftNamespacedKey.fromMinecraft(this.id), result, CraftRecipe.toBukkit(this.ingredient), this.experience, this.cookingTime);
      recipe.setGroup(this.group);
      recipe.setCategory(CraftRecipe.getCategory(this.category()));

      return recipe;
   }
   // CraftBukkit end
}
