package net.minecraft.core.dispenser;

import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;

public abstract class AbstractProjectileDispenseBehavior extends DefaultDispenseItemBehavior {
   public ItemStack execute(BlockSource p_123366_, ItemStack p_123367_) {
      Level level = p_123366_.getLevel();
      Position position = DispenserBlock.getDispensePosition(p_123366_);
      Direction direction = p_123366_.getBlockState().getValue(DispenserBlock.FACING);
      Projectile projectile = this.getProjectile(level, position, p_123367_);

      // CraftBukkit start
      ItemStack itemstack1 = p_123367_.split(1);
      org.bukkit.block.Block block = CraftBlock.at(level, p_123366_.getPos());
      CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);
      
      BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector((double) direction.getStepX(), (double) ((float) direction.getStepY() + 0.1F), (double) direction.getStepZ()));
      if (!DispenserBlock.eventFired) {
         level.getCraftServer().getPluginManager().callEvent(event);
      }
      
      if (event.isCancelled()) {
         p_123367_.grow(1);
         return p_123367_;
      }
      
      if (!event.getItem().equals(craftItem)) {
         p_123367_.grow(1);
         // Chain to handler for new item
         ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
         DispenseItemBehavior idispensebehavior = DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
         if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
            idispensebehavior.dispense(p_123366_, eventStack);
            return p_123367_;
         }
      }

      projectile.shoot(event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), this.getPower(), this.getUncertainty());
      projectile.projectileSource = new org.bukkit.craftbukkit.v1_20_R1.projectiles.CraftBlockProjectileSource((DispenserBlockEntity) p_123366_.getEntity());
      // CraftBukkit end
      level.addFreshEntity(projectile);
      return p_123367_;
   }

   protected void playSound(BlockSource p_123364_) {
      p_123364_.getLevel().levelEvent(1002, p_123364_.getPos(), 0);
   }

   protected abstract Projectile getProjectile(Level p_123360_, Position p_123361_, ItemStack p_123362_);

   protected float getUncertainty() {
      return 6.0F;
   }

   protected float getPower() {
      return 1.1F;
   }
}
