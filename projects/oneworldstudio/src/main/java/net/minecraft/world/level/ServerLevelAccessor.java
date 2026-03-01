package net.minecraft.world.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;

public interface ServerLevelAccessor extends LevelAccessor {
   ServerLevel getLevel();

   default void addFreshEntityWithPassengers(Entity p_47206_) {
      p_47206_.getSelfAndPassengers().forEach(this::addFreshEntity);
   }

   default void addFreshEntityWithPassengers(Entity pEntity, CreatureSpawnEvent.SpawnReason reason) {
      pEntity.getSelfAndPassengers().forEach((e) -> this.addFreshEntity(e, reason));
   }

   @Override
   default ServerLevel getMinecraftWorld() {
      return getLevel();
   }
   // CraftBukkit end
}
