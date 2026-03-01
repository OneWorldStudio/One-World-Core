package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.event.entity.EntityInteractEvent;

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {
   public static final IntegerProperty POWER = BlockStateProperties.POWER;
   private final int maxWeight;

   public WeightedPressurePlateBlock(int p_273669_, BlockBehaviour.Properties p_273512_, BlockSetType p_272868_) {
      super(p_273512_, p_272868_);
      this.registerDefaultState(this.stateDefinition.any().setValue(POWER, Integer.valueOf(0)));
      this.maxWeight = p_273669_;
   }

   protected int getSignalStrength(Level p_58213_, BlockPos p_58214_) {
      // CraftBukkit start
      // int i = Math.min(pLevel.getEntitiesOfClass(Entity.class, TOUCH_AABB.move(pPos)).size(), this.maxWeight);
      int i = 0;
      java.util.Iterator iterator = p_58213_.getEntitiesOfClass(Entity.class, TOUCH_AABB.move(p_58214_)).iterator();

      while (iterator.hasNext()) {
         Entity entity = (Entity) iterator.next();

         org.bukkit.event.Cancellable cancellable;

         if (entity instanceof Player player) {
            cancellable = org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory.callPlayerInteractEvent(player, org.bukkit.event.block.Action.PHYSICAL, p_58214_, null, null, null);
         } else {
            cancellable = new EntityInteractEvent(entity.getBukkitEntity(), CraftBlock.at(p_58213_, p_58214_));
            p_58213_.getCraftServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
         }

         // We only want to block turning the plate on if all events are cancelled
         if (!cancellable.isCancelled()) {
            i++;
         }
      }

      i = Math.min(i, this.maxWeight);
      // CraftBukkit end
      if (i > 0) {
         float f = (float)Math.min(this.maxWeight, i) / (float)this.maxWeight;
         return Mth.ceil(f * 15.0F);
      } else {
         return 0;
      }
   }

   protected int getSignalForState(BlockState p_58220_) {
      return p_58220_.getValue(POWER);
   }

   protected BlockState setSignalForState(BlockState p_58208_, int p_58209_) {
      return p_58208_.setValue(POWER, Integer.valueOf(p_58209_));
   }

   protected int getPressedTime() {
      return 10;
   }

   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_58211_) {
      p_58211_.add(POWER);
   }
}
