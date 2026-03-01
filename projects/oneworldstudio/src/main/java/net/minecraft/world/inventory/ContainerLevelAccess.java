package net.minecraft.world.inventory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;

public interface ContainerLevelAccess {
   ContainerLevelAccess NULL = new ContainerLevelAccess() {
      public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> p_39304_) {
         return Optional.empty();
      }
   };

   // Mohist start
   static ContainerLevelAccess createFix(final Level p_39290_, final BlockPos p_39291_) {
      ContainerLevelAccess instance = create(p_39290_, p_39291_);
      return new ContainerLevelAccess() {
         public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> p_39311_) {
            return instance.evaluate(p_39311_);
         }
         // CraftBukkit start
         @Override
         public Level getWorld() {
            return p_39290_;
         }

         @Override
         public BlockPos getPosition() {
            return p_39291_;
         }
         // CraftBukkit end
      };
   }
   // Mohist end

   static ContainerLevelAccess create(final Level p_39290_, final BlockPos p_39291_) {
      return new ContainerLevelAccess() {
         public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> p_39311_) {
            return Optional.of(p_39311_.apply(p_39290_, p_39291_));
         }
         // CraftBukkit start
         @Override
         public Level getWorld() {
            return p_39290_;
         }

         @Override
         public BlockPos getPosition() {
            return p_39291_;
         }
         // CraftBukkit end
      };
   }

   <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> p_39298_);

   default <T> T evaluate(BiFunction<Level, BlockPos, T> p_39300_, T p_39301_) {
      return this.evaluate(p_39300_).orElse(p_39301_);
   }

   default void execute(BiConsumer<Level, BlockPos> p_39293_) {
      this.evaluate((p_39296_, p_39297_) -> {
         p_39293_.accept(p_39296_, p_39297_);
         return Optional.empty();
      });
   }

   // CraftBukkit start
   default Level getWorld() {
     return this.evaluate((level, blockpos) -> level).orElse(null); // Mohist
   }

   default BlockPos getPosition() {
      return this.evaluate((level, blockpos) -> blockpos).orElse(null); // Mohist
   }

   default org.bukkit.Location getLocation() {
      BlockPos blockPos = getPosition();
      if (blockPos == null) {
         return null;
      } else {
         Level level = getWorld();
         CraftWorld world = level == null ? null : level.getWorld();
         return new org.bukkit.Location(world, blockPos.getX(), blockPos.getY(), blockPos.getZ());
      }
   }
   // CraftBukkit end
}
