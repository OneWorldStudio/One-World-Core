package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.bukkit.craftbukkit.v1_20_R1.CraftEquipmentSlot;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.event.hanging.HangingPlaceEvent;

import java.util.concurrent.atomic.AtomicReference;

public class LeadItem extends Item {
   public LeadItem(Item.Properties p_42828_) {
      super(p_42828_);
   }

   public InteractionResult useOn(UseOnContext p_42834_) {
      Level level = p_42834_.getLevel();
      BlockPos blockpos = p_42834_.getClickedPos();
      BlockState blockstate = level.getBlockState(blockpos);
      if (blockstate.is(BlockTags.FENCES)) {
         Player player = p_42834_.getPlayer();
         if (!level.isClientSide && player != null) {
             enumHand.set(p_42834_.getHand());  // CraftBukkit - Pass hand
             bindPlayerMobs(player, level, blockpos);
         }

         return InteractionResult.sidedSuccess(level.isClientSide);
      } else {
         return InteractionResult.PASS;
      }
   }

   public static AtomicReference<net.minecraft.world.InteractionHand> enumHand = new AtomicReference<>(net.minecraft.world.InteractionHand.MAIN_HAND);
   public static InteractionResult bindPlayerMobs(Player p_42830_, Level p_42831_, BlockPos p_42832_) {
      LeashFenceKnotEntity leashfenceknotentity = null;
      boolean flag = false;
      double d0 = 7.0D;
      int i = p_42832_.getX();
      int j = p_42832_.getY();
      int k = p_42832_.getZ();
      net.minecraft.world.InteractionHand interactionHand = enumHand.getAndSet(net.minecraft.world.InteractionHand.MAIN_HAND);

      for(Mob mob : p_42831_.getEntitiesOfClass(Mob.class, new AABB((double)i - 7.0D, (double)j - 7.0D, (double)k - 7.0D, (double)i + 7.0D, (double)j + 7.0D, (double)k + 7.0D))) {
         if (mob.getLeashHolder() == p_42830_) {
            if (leashfenceknotentity == null) {
               leashfenceknotentity = LeashFenceKnotEntity.getOrCreateKnot(p_42831_, p_42832_);

               // CraftBukkit start - fire HangingPlaceEvent
               HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) leashfenceknotentity.getBukkitEntity(), p_42830_ != null ? (org.bukkit.entity.Player) p_42830_.getBukkitEntity() : null, CraftBlock.at(p_42831_, p_42832_), org.bukkit.block.BlockFace.SELF, CraftEquipmentSlot.getHand(interactionHand));
               p_42831_.getCraftServer().getPluginManager().callEvent(event);

               if (event.isCancelled()) {
                  leashfenceknotentity.discard();
                  return InteractionResult.PASS;
               }
               // CraftBukkit end
               leashfenceknotentity.playPlacementSound();
            }

            // CraftBukkit start
            if (org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory.callPlayerLeashEntityEvent(mob, leashfenceknotentity, p_42830_, interactionHand).isCancelled()) {
               continue;
            }
            // CraftBukkit end

            mob.setLeashedTo(leashfenceknotentity, true);
            flag = true;
         }
      }

      if (flag) {
         p_42831_.gameEvent(GameEvent.BLOCK_ATTACH, p_42832_, GameEvent.Context.of(p_42830_));
      }

      return flag ? InteractionResult.SUCCESS : InteractionResult.PASS;
   }

   // CraftBukkit start
   public static InteractionResult bindPlayerMobs(Player pPlayer, Level pLevel, BlockPos pPos, net.minecraft.world.InteractionHand enumhand) {
      enumHand.set(enumhand);
      return bindPlayerMobs(pPlayer, pLevel, pPos);
   }
   // CraftBukkit end
}
