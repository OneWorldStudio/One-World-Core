package net.minecraft.world.item.crafting;

import com.oneworldstudiomc.bukkit.inventory.MohistSpecialRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftCampfireRecipe;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.inventory.Recipe;

public class CampfireCookingRecipe extends AbstractCookingRecipe {
   public CampfireCookingRecipe(ResourceLocation p_249468_, String p_250140_, CookingBookCategory p_251808_, Ingredient p_249826_, ItemStack p_251839_, float p_251432_, int p_251471_) {
      super(RecipeType.CAMPFIRE_COOKING, p_249468_, p_250140_, p_251808_, p_249826_, p_251839_, p_251432_, p_251471_);
   }

   public ItemStack getToastSymbol() {
      return new ItemStack(Blocks.CAMPFIRE);
   }

   public RecipeSerializer<?> getSerializer() {
      return RecipeSerializer.CAMPFIRE_COOKING_RECIPE;
   }

   // CraftBukkit start
   @Override
   public Recipe toBukkitRecipe() {
      if (this.result.isEmpty()) {
         return new MohistSpecialRecipe(this);
      }
      CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

      CraftCampfireRecipe recipe = new CraftCampfireRecipe(CraftNamespacedKey.fromMinecraft(this.id), result, CraftRecipe.toBukkit(this.ingredient), this.experience, this.cookingTime);
      recipe.setGroup(this.group);
      recipe.setCategory(CraftRecipe.getCategory(this.category()));
      
      return recipe;
   }
   // CraftBukkit end
}
