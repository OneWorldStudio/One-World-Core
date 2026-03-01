package net.minecraft.world.level.portal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftPortalEvent;

public class PortalInfo {
   public final Vec3 pos;
   public final Vec3 speed;
   public final float yRot;
   public final float xRot;
   public ServerLevel world;
   public CraftPortalEvent portalEventInfo;

   public PortalInfo(Vec3 p_77681_, Vec3 p_77682_, float p_77683_, float p_77684_) {
      this.pos = p_77681_;
      this.speed = p_77682_;
      this.yRot = p_77683_;
      this.xRot = p_77684_;
   }

   public void setPortalEventInfo(CraftPortalEvent event) {
      this.portalEventInfo = event;
   }

   public CraftPortalEvent getPortalEventInfo() {
      return this.portalEventInfo;
   }

   public void setWorld(ServerLevel world) {
      this.world = world;
   }

   public ServerLevel getWorld() {
      return this.world;
   }

}
