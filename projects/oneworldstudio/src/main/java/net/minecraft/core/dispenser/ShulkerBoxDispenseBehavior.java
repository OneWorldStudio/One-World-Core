package net.minecraft.core.dispenser;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.DispenserBlock;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
import org.slf4j.Logger;

public class ShulkerBoxDispenseBehavior extends OptionalDispenseItemBehavior {
   private static final Logger LOGGER = LogUtils.getLogger();

   protected ItemStack execute(BlockSource p_123587_, ItemStack p_123588_) {
      this.setSuccess(false);
      Item item = p_123588_.getItem();
      if (item instanceof BlockItem) {
         Direction direction = p_123587_.getBlockState().getValue(DispenserBlock.FACING);
         BlockPos blockpos = p_123587_.getPos().relative(direction);
         Direction direction1 = p_123587_.getLevel().isEmptyBlock(blockpos.below()) ? direction : Direction.UP;

         // CraftBukkit start
         org.bukkit.block.Block bukkitBlock = p_123587_.getLevel().getWorld().getBlockAt(p_123587_.getPos().getX(), p_123587_.getPos().getY(), p_123587_.getPos().getZ());
         CraftItemStack craftItem = CraftItemStack.asCraftMirror(p_123588_);

         BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockpos.getX(), blockpos.getY(), blockpos.getZ()));
         if (!DispenserBlock.eventFired) {
            p_123587_.getLevel().getCraftServer().getPluginManager().callEvent(event);
         }

         if (event.isCancelled()) {
            return p_123588_;
         }

         if (!event.getItem().equals(craftItem)) {
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
               idispensebehavior.dispense(p_123587_, eventStack);
               return p_123588_;
            }
         }
         // CraftBukkit end

         try {
            this.setSuccess(((BlockItem)item).place(new DirectionalPlaceContext(p_123587_.getLevel(), blockpos, direction, p_123588_, direction1)).consumesAction());
         } catch (Exception exception) {
            LOGGER.error("Error trying to place shulker box at {}", blockpos, exception);
         }
      }

      return p_123588_;
   }
}
