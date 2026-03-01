package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;

public class BoatDispenseItemBehavior extends DefaultDispenseItemBehavior {
   private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
   private final Boat.Type type;
   private final boolean isChestBoat;

   public BoatDispenseItemBehavior(Boat.Type p_123371_) {
      this(p_123371_, false);
   }

   public BoatDispenseItemBehavior(Boat.Type p_235891_, boolean p_235892_) {
      this.type = p_235891_;
      this.isChestBoat = p_235892_;
   }

   public ItemStack execute(BlockSource p_123375_, ItemStack p_123376_) {
      Direction direction = p_123375_.getBlockState().getValue(DispenserBlock.FACING);
      Level level = p_123375_.getLevel();
      double d0 = 0.5625D + (double)EntityType.BOAT.getWidth() / 2.0D;
      double d1 = p_123375_.x() + (double)direction.getStepX() * d0;
      double d2 = p_123375_.y() + (double)((float)direction.getStepY() * 1.125F);
      double d3 = p_123375_.z() + (double)direction.getStepZ() * d0;
      BlockPos blockpos = p_123375_.getPos().relative(direction);
      // CraftBukkit start
      ItemStack itemstack1 = p_123376_.split(1);
      org.bukkit.block.Block block = CraftBlock.at(level, p_123375_.getPos());
      CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

      BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(d0, d1, d2));
      if (!DispenserBlock.eventFired) {
         level.getCraftServer().getPluginManager().callEvent(event);
      }

      if (event.isCancelled()) {
         p_123376_.grow(1);
         return p_123376_;
      }

      if (!event.getItem().equals(craftItem)) {
         p_123376_.grow(1);
         // Chain to handler for new item
         ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
         DispenseItemBehavior idispensebehavior = DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
         if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
            idispensebehavior.dispense(p_123375_, eventStack);
            return p_123376_;
         }
      }

      Boat boat = this.isChestBoat ? new ChestBoat(level, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ()) : new Boat(level, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ());
      // CraftBukkit end
      boat.setVariant(this.type);
      boat.setYRot(direction.toYRot());
      double d4;
      if (boat.canBoatInFluid(level.getFluidState(blockpos))) {
         d4 = 1.0D;
      } else {
         if (!level.getBlockState(blockpos).isAir() || !boat.canBoatInFluid(level.getFluidState(blockpos.below()))) {
            return this.defaultDispenseItemBehavior.dispense(p_123375_, p_123376_);
         }

         d4 = 0.0D;
      }

      boat.setPos(d1, d2 + d4, d3);
      if (!level.addFreshEntity(boat)) p_123376_.grow(1); // CraftBukkit
      return p_123376_;
   }

   protected void playSound(BlockSource p_123373_) {
      p_123373_.getLevel().levelEvent(1000, p_123373_.getPos(), 0);
   }
}
