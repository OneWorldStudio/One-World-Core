package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Ageable;
import org.bukkit.event.player.PlayerEggThrowEvent;

public class ThrownEgg extends ThrowableItemProjectile {
   public ThrownEgg(EntityType<? extends ThrownEgg> p_37473_, Level p_37474_) {
      super(p_37473_, p_37474_);
   }

   public ThrownEgg(Level p_37481_, LivingEntity p_37482_) {
      super(EntityType.EGG, p_37482_, p_37481_);
   }

   public ThrownEgg(Level p_37476_, double p_37477_, double p_37478_, double p_37479_) {
      super(EntityType.EGG, p_37477_, p_37478_, p_37479_, p_37476_);
   }

   public void handleEntityEvent(byte p_37484_) {
      if (p_37484_ == 3) {
         double d0 = 0.08D;

         for(int i = 0; i < 8; ++i) {
            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(), this.getZ(), ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D);
         }
      }

   }

   protected void onHitEntity(EntityHitResult p_37486_) {
      super.onHitEntity(p_37486_);
      p_37486_.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
   }

   protected void onHit(HitResult p_37488_) {
      super.onHit(p_37488_);
      if (!this.level.isClientSide) {
         boolean hatching = this.random.nextInt(8) == 0; // CraftBukkit
         if (true) {
            byte i = 1;
            if (this.random.nextInt(32) == 0) {
               i = 4;
            }

            // CraftBukkit start
            org.bukkit.entity.EntityType hatchingType = org.bukkit.entity.EntityType.CHICKEN;

            Entity shooter = this.getOwner();
            if (!hatching) {
               i = 0;
            }

            if (shooter instanceof ServerPlayer) {
               PlayerEggThrowEvent event = new PlayerEggThrowEvent((org.bukkit.entity.Player) shooter.getBukkitEntity(), (org.bukkit.entity.Egg) this.getBukkitEntity(), hatching, i, hatchingType);
               Bukkit.getPluginManager().callEvent(event);

               i = event.getNumHatches();
               hatching = event.isHatching();
               hatchingType = event.getHatchingType();
               // If hatching is set to false, ensure child count is 0
               if (!hatching) {
                  i = 0;
               }
            }
            // CraftBukkit end

            if (hatching) {
               for (int i1 = 0; i1 < i; ++i1) {
                  net.minecraft.world.entity.EntityType<?> entityType = EntityType.CHICKEN;
                  Chicken chicken = (Chicken) entityType.create(this.level());
                  if (entityType == EntityType.CHICKEN) {
                     chicken = (Chicken) level.getWorld().createEntity(new org.bukkit.Location(level().getWorld(), this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F), hatchingType.getEntityClass());
                  }
                  if (chicken != null) {
                     // CraftBukkit start
                     if (chicken.getBukkitEntity() instanceof Ageable) {
                        ((Ageable) chicken.getBukkitEntity()).setBaby();
                     }
                     chicken.spawnReason(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG);
                     level().addFreshEntity(chicken);
                     // CraftBukkit end
                  }
               }
            }
         }

         this.level().broadcastEntityEvent(this, (byte)3);
         this.discard();
      }

   }

   protected Item getDefaultItem() {
      return Items.EGG;
   }
}
