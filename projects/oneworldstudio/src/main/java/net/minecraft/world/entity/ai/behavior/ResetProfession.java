package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftVillager;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

public class ResetProfession {
   public static BehaviorControl<Villager> create() {
      return BehaviorBuilder.create((p_259684_) -> {
         return p_259684_.group(p_259684_.absent(MemoryModuleType.JOB_SITE)).apply(p_259684_, (p_260035_) -> {
            return (p_260244_, p_260084_, p_259597_) -> {
               VillagerData villagerdata = p_260084_.getVillagerData();
               if (villagerdata.getProfession() != VillagerProfession.NONE && villagerdata.getProfession() != VillagerProfession.NITWIT && p_260084_.getVillagerXp() == 0 && villagerdata.getLevel() <= 1) {
                  // CraftBukkit start
                  VillagerCareerChangeEvent event = CraftEventFactory.callVillagerCareerChangeEvent(p_260084_, CraftVillager.nmsToBukkitProfession(VillagerProfession.NONE), VillagerCareerChangeEvent.ChangeReason.LOSING_JOB);
                  if (event.isCancelled()) {
                     return false;
                  }
                  p_260084_.setVillagerData(p_260084_.getVillagerData().setProfession(CraftVillager.bukkitToNmsProfession(event.getProfession())));
                  // CraftBukkit end
                  p_260084_.refreshBrain(p_260244_);
                  return true;
               } else {
                  return false;
               }
            };
         });
      });
   }
}
