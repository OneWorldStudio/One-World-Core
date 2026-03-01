package net.minecraft.world.level.block.grower;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.bukkit.TreeType;

public abstract class AbstractTreeGrower {

   @Nullable
   protected abstract ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource p_222910_, boolean p_222911_);

   public boolean growTree(ServerLevel p_222905_, ChunkGenerator p_222906_, BlockPos p_222907_, BlockState p_222908_, RandomSource p_222909_) {
      ResourceKey<ConfiguredFeature<?, ?>> resourcekey = this.getConfiguredFeature(p_222909_, this.hasFlowers(p_222905_, p_222907_));
      if (resourcekey == null) {
         return false;
      } else {
         Holder<ConfiguredFeature<?, ?>> holder = p_222905_.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(resourcekey).orElse((Holder.Reference<ConfiguredFeature<?, ?>>)null);
         var event = net.minecraftforge.event.ForgeEventFactory.blockGrowFeature(p_222905_, p_222909_, p_222907_, holder);
         holder = event.getFeature();
         if (event.getResult() == net.minecraftforge.eventbus.api.Event.Result.DENY) return false;
         if (holder == null) {
            return false;
         } else {
            setTreeType(holder); // CraftBukkit
            ConfiguredFeature<?, ?> configuredfeature = holder.value();
            BlockState blockstate = p_222905_.getFluidState(p_222907_).createLegacyBlock();
            p_222905_.setBlock(p_222907_, blockstate, 4);
            if (configuredfeature.place(p_222905_, p_222906_, p_222909_, p_222907_)) {
               if (p_222905_.getBlockState(p_222907_) == blockstate) {
                  p_222905_.sendBlockUpdated(p_222907_, p_222908_, blockstate, 2);
               }

               return true;
            } else {
               p_222905_.setBlock(p_222907_, p_222908_, 4);
               return false;
            }
         }
      }
   }

   private boolean hasFlowers(LevelAccessor p_60012_, BlockPos p_60013_) {
      for(BlockPos blockpos : BlockPos.MutableBlockPos.betweenClosed(p_60013_.below().north(2).west(2), p_60013_.above().south(2).east(2))) {
         if (p_60012_.getBlockState(blockpos).is(BlockTags.FLOWERS)) {
            return true;
         }
      }

      return false;
   }

   // CraftBukkit start
   protected void setTreeType(Holder<ConfiguredFeature<?, ?>> holder) {
      if (holder.unwrapKey().isEmpty()) return;
      ResourceKey<ConfiguredFeature<?, ?>> worldgentreeabstract = holder.unwrapKey().get();
      if (worldgentreeabstract == TreeFeatures.OAK || worldgentreeabstract == TreeFeatures.OAK_BEES_005) {
         SaplingBlock.treeType = TreeType.TREE;
      } else if (worldgentreeabstract == TreeFeatures.HUGE_RED_MUSHROOM) {
         SaplingBlock.treeType = TreeType.RED_MUSHROOM;
      } else if (worldgentreeabstract == TreeFeatures.HUGE_BROWN_MUSHROOM) {
         SaplingBlock.treeType = TreeType.BROWN_MUSHROOM;
      } else if (worldgentreeabstract == TreeFeatures.JUNGLE_TREE) {
         SaplingBlock.treeType = TreeType.COCOA_TREE;
      } else if (worldgentreeabstract == TreeFeatures.JUNGLE_TREE_NO_VINE) {
         SaplingBlock.treeType = TreeType.SMALL_JUNGLE;
      } else if (worldgentreeabstract == TreeFeatures.PINE) {
         SaplingBlock.treeType = TreeType.TALL_REDWOOD;
      } else if (worldgentreeabstract == TreeFeatures.SPRUCE) {
         SaplingBlock.treeType = TreeType.REDWOOD;
      } else if (worldgentreeabstract == TreeFeatures.ACACIA) {
         SaplingBlock.treeType = TreeType.ACACIA;
      } else if (worldgentreeabstract == TreeFeatures.BIRCH || worldgentreeabstract == TreeFeatures.BIRCH_BEES_005) {
         SaplingBlock.treeType = TreeType.BIRCH;
      } else if (worldgentreeabstract == TreeFeatures.SUPER_BIRCH_BEES_0002) {
         SaplingBlock.treeType = TreeType.TALL_BIRCH;
      } else if (worldgentreeabstract == TreeFeatures.SWAMP_OAK) {
         SaplingBlock.treeType = TreeType.SWAMP;
      } else if (worldgentreeabstract == TreeFeatures.FANCY_OAK || worldgentreeabstract == TreeFeatures.FANCY_OAK_BEES_005) {
         SaplingBlock.treeType = TreeType.BIG_TREE;
      } else if (worldgentreeabstract == TreeFeatures.JUNGLE_BUSH) {
         SaplingBlock.treeType = TreeType.JUNGLE_BUSH;
      } else if (worldgentreeabstract == TreeFeatures.DARK_OAK) {
         SaplingBlock.treeType = TreeType.DARK_OAK;
      } else if (worldgentreeabstract == TreeFeatures.MEGA_SPRUCE) {
         SaplingBlock.treeType = TreeType.MEGA_REDWOOD;
      } else if (worldgentreeabstract == TreeFeatures.MEGA_PINE) {
         SaplingBlock.treeType = TreeType.MEGA_REDWOOD;
      } else if (worldgentreeabstract == TreeFeatures.MEGA_JUNGLE_TREE) {
         SaplingBlock.treeType = TreeType.JUNGLE;
      } else if (worldgentreeabstract == TreeFeatures.AZALEA_TREE) {
         SaplingBlock.treeType = TreeType.AZALEA;
      } else if (worldgentreeabstract == TreeFeatures.MANGROVE) {
         SaplingBlock.treeType = TreeType.MANGROVE;
      } else if (worldgentreeabstract == TreeFeatures.TALL_MANGROVE) {
         SaplingBlock.treeType = TreeType.TALL_MANGROVE;
      } else if (worldgentreeabstract == TreeFeatures.CHERRY || worldgentreeabstract == TreeFeatures.CHERRY_BEES_005) {
         SaplingBlock.treeType = TreeType.CHERRY;
      } else {
         SaplingBlock.treeType = TreeType.CUSTOM; // Mohist - handle mod tree generator
      }
   }
   // CraftBukkit end
}
