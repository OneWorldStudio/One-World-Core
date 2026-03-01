package net.minecraft.world.item.crafting;

import com.oneworldstudiomc.bukkit.inventory.MohistSpecialRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftBlastingRecipe;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.inventory.Recipe;

public class BlastingRecipe extends AbstractCookingRecipe {
   public BlastingRecipe(ResourceLocation p_249728_, String p_251053_, CookingBookCategory p_249936_, Ingredient p_251550_, ItemStack p_251027_, float p_250843_, int p_249841_) {
      super(RecipeType.BLASTING, p_249728_, p_251053_, p_249936_, p_251550_, p_251027_, p_250843_, p_249841_);
   }

   public ItemStack getToastSymbol() {
      return new ItemStack(Blocks.BLAST_FURNACE);
   }

   public RecipeSerializer<?> getSerializer() {
      return RecipeSerializer.BLASTING_RECIPE;
   }

   // CraftBukkit start
   @Override
   public Recipe toBukkitRecipe() {
      if (this.result.isEmpty()) {
         return new MohistSpecialRecipe(this);
      }
      CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

      CraftBlastingRecipe recipe = new CraftBlastingRecipe(CraftNamespacedKey.fromMinecraft(this.id), result, CraftRecipe.toBukkit(this.ingredient), this.experience, this.cookingTime);
      recipe.setGroup(this.group);
      recipe.setCategory(CraftRecipe.getCategory(this.category()));

      return recipe;
   }
   // CraftBukkit end
}
