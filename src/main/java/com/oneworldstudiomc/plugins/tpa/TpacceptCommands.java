package com.oneworldstudiomc.plugins.tpa;

import com.oneworldstudiomc.MohistConfig;
import com.oneworldstudiomc.util.I18n;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TpacceptCommands extends Command {

    public TpacceptCommands(String name) {
        super(name);
        this.usageMessage = "/tpaccept";
        if (MohistConfig.tpa_permissions_enable) {
            this.setPermission("mohist.command.tpa");
        }
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (sender instanceof Player player) {
            if (TpaComamands.tpa.containsKey(player)) {
                final Player a = TpaComamands.tpa.get(player);
                a.teleport(player);
                player.sendMessage(I18n.as("tpacceptcommands.successfully.me"));
                a.sendMessage(I18n.as("tpacceptcommands.successfully.you"));
                TpaComamands.tpa.remove(player);
            }
            else {
                sender.sendMessage(I18n.as("tpacceptcommands.nokey"));
            }
        }
        return false;
    }
}
