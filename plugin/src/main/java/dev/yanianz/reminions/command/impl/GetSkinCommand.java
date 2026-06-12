package dev.yanianz.reminions.command.impl;

import java.util.List;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.config.MinionSkinConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GetSkinCommand extends CommandHolder {

    public GetSkinCommand() {
        super("skin", "minions.command.skin", 3, "<player_name> <skin_id> <notify>");
    }

    @Override
    public void onExecute(CommandSender sender, String[] args, ReMinions plugin) {
        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            plugin.getConfig0().sendMessage(sender, "command_player_not_found", "%player%", playerName);
            return;
        }

        String skinId = args[1];
        MinionSkinConfig skinConfig = plugin.getSkinManager().get(skinId);
        if (skinConfig == null) {
            plugin.getConfig0().sendMessage(sender, "skin_id_not_found", "%skin_id%", skinId);
            return;
        }

        if (target.getInventory().firstEmpty() == -1) {
            plugin.getConfig0().sendMessage(sender, "inventory_full");
            return;
        }

        target.getInventory().addItem(skinConfig.getHead());

        if (Boolean.parseBoolean(args[2])) {
            plugin.getConfig0().sendMessage(target, "received_skin", "%skin_name%", skinId);
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        return switch (args.length) {
            case 1 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[0])).toList();
            case 2 -> plugin.getSkinManager().keys().stream().filter(k -> k.startsWith(args[1])).toList();
            case 3 -> Stream.of("true", "false").filter(s -> s.startsWith(args[2])).toList();
            default -> List.of();
        };
    }
}
