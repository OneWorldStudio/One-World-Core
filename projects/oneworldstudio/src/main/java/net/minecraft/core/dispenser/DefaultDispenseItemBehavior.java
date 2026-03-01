package net.minecraft.core.dispenser;

import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftVector;
import org.bukkit.event.block.BlockDispenseEvent;

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {

   // CraftBukkit start // Mohist TODO super();
   private boolean dropper = true;

   public DefaultDispenseItemBehavior() {
      this(true);
   }

   public DefaultDispenseItemBehavior(boolean dropper) {
      this.dropper = dropper;
   }

  public final ItemStack dispense(BlockSource p_123391_, ItemStack p_123392_) {
      ItemStack itemstack = this.execute(p_123391_, p_123392_);
      this.playSound(p_123391_);
      this.playAnimation(p_123391_, p_123391_.getBlockState().getValue(DispenserBlock.FACING));
      return itemstack;
   }

   protected ItemStack execute(BlockSource p_123385_, ItemStack p_123386_) {
      Direction direction = p_123385_.getBlockState().getValue(DispenserBlock.FACING);
      Position position = DispenserBlock.getDispensePosition(p_123385_);
      ItemStack itemstack = p_123386_.split(1);
      // CraftBukkit start
      if (!spawnItem(p_123385_.getLevel(), itemstack, 6, direction, p_123385_, dropper)){
         itemstack.grow(1);
      }
      // CraftBukkit end
      return p_123386_;
   }

   public static void spawnItem(Level p_123379_, ItemStack p_123380_, int p_123381_, Direction p_123382_, Position p_123383_) {
      double d0 = p_123383_.x();
      double d1 = p_123383_.y();
      double d2 = p_123383_.z();
      if (p_123382_.getAxis() == Direction.Axis.Y) {
         d1 -= 0.125D;
      } else {
         d1 -= 0.15625D;
      }

      ItemEntity itementity = new ItemEntity(p_123379_, d0, d1, d2, p_123380_);
      double d3 = p_123379_.random.nextDouble() * 0.1D + 0.2D;
      itementity.setDeltaMovement(p_123379_.random.triangle((double)p_123382_.getStepX() * d3, 0.0172275D * (double)p_123381_), p_123379_.random.triangle(0.2D, 0.0172275D * (double)p_123381_), p_123379_.random.triangle((double)p_123382_.getStepZ() * d3, 0.0172275D * (double)p_123381_));
      p_123379_.addFreshEntity(itementity);
   }

   // Mohist start TODO
   // CraftBukkit start
   public static boolean spawnItem(Level pLevel, ItemStack pStack, int pSpeed, Direction pFacing, BlockSource isourceblock, boolean dropper) {
      if (pStack.isEmpty()) return true;
      Position iposition = DispenserBlock.getDispensePosition(isourceblock);
      // CraftBukkit end
      double d0 = iposition.x();
      double d1 = iposition.y();
      double d2 = iposition.z();
      if (pFacing.getAxis() == Direction.Axis.Y) {
         d1 -= 0.125D;
      } else {
         d1 -= 0.15625D;
      }

      ItemEntity itementity = new ItemEntity(pLevel, d0, d1, d2, pStack);
      double d3 = pLevel.random.nextDouble() * 0.1D + 0.2D;
      itementity.setDeltaMovement(pLevel.random.triangle((double)pFacing.getStepX() * d3, 0.0172275D * (double)pSpeed), pLevel.random.triangle(0.2D, 0.0172275D * (double)pSpeed), pLevel.random.triangle((double)pFacing.getStepZ() * d3, 0.0172275D * (double)pSpeed));

      // CraftBukkit start
      org.bukkit.block.Block block = CraftBlock.at(pLevel, isourceblock.getPos());
      CraftItemStack craftItem = CraftItemStack.asCraftMirror(pStack);

      BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), CraftVector.toBukkit(itementity.getDeltaMovement()));
      if (!DispenserBlock.eventFired) {
         pLevel.getCraftServer().getPluginManager().callEvent(event);
      }

      if (event.isCancelled()) {
         return false;
      }

      itementity.setItem(CraftItemStack.asNMSCopy(event.getItem()));
      itementity.setDeltaMovement(CraftVector.toNMS(event.getVelocity()));

      if (!dropper && !event.getItem().getType().equals(craftItem.getType())) {
         // Chain to handler for new item
         ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
         DispenseItemBehavior idispensebehavior = DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
         if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior.getClass() != DefaultDispenseItemBehavior.class) {
            idispensebehavior.dispense(isourceblock, eventStack);
         } else {
            pLevel.addFreshEntity(itementity);
         }
         return false;
      }

      pLevel.addFreshEntity(itementity);
      return true;
   }
   // CraftBukkit end
   // Mohist end

   protected void playSound(BlockSource p_123384_) {
      p_123384_.getLevel().levelEvent(1000, p_123384_.getPos(), 0);
   }

   protected void playAnimation(BlockSource p_123388_, Direction p_123389_) {
      p_123388_.getLevel().levelEvent(2000, p_123388_.getPos(), p_123389_.get3DDataValue());
   }
}
