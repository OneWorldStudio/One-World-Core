package net.minecraft.commands;

import net.minecraft.network.chat.Component;

public interface CommandSource {
   CommandSource NULL = new CommandSource() {
      public void sendSystemMessage(Component p_230799_) {
      }

      public boolean acceptsSuccess() {
         return false;
      }

      public boolean acceptsFailure() {
         return false;
      }

      public boolean shouldInformAdmins() {
         return false;
      }

      // CraftBukkit start
      @Override
      public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
         return wrapper.getServer().console;
      }
      // CraftBukkit end
   };

   void sendSystemMessage(Component p_230797_);

   boolean acceptsSuccess();

   boolean acceptsFailure();

   boolean shouldInformAdmins();

   default boolean alwaysAccepts() {
      return false;
   }

   default org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
      return NULL.getBukkitSender(wrapper);
   }
}
