package net.minecraft.world;

import javax.annotation.concurrent.Immutable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;

@Immutable
public class LockCode {
   public static final LockCode NO_LOCK = new LockCode("");
   public static final String TAG_LOCK = "Lock";
   public final String key;

   public LockCode(String p_19106_) {
      this.key = p_19106_;
   }

   public boolean unlocksWith(ItemStack p_19108_) {
      // CraftBukkit start - SPIGOT-6307: Check for color codes if the lock contains color codes
      if (this.key.isEmpty()) return true;
      if (!p_19108_.isEmpty() && p_19108_.hasCustomHoverName()) {
         if (this.key.indexOf(ChatColor.COLOR_CHAR) == -1) {
            // The lock key contains no color codes, so let's ignore colors in the item display name (vanilla Minecraft behavior):
            return this.key.equals(p_19108_.getHoverName().getString());
         } else {
            // The lock key contains color codes, so let's take them into account:
            return this.key.equals(CraftChatMessage.fromComponent(p_19108_.getHoverName()));
         }
      }
      return false;
      // CraftBukkit end
   }

   public void addToTag(CompoundTag p_19110_) {
      if (!this.key.isEmpty()) {
         p_19110_.putString("Lock", this.key);
      }

   }

   public static LockCode fromTag(CompoundTag p_19112_) {
      return p_19112_.contains("Lock", 8) ? new LockCode(p_19112_.getString("Lock")) : NO_LOCK;
   }
}
