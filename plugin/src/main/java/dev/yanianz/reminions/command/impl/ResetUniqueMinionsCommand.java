package dev.yanianz.reminions.command.impl;

import java.util.List;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.core.player.PlayerMinions;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetUniqueMinionsCommand extends CommandHolder {

    public ResetUniqueMinionsCommand() {
        super("reset_unique", "minions.command.reset_unique", 1, "<player_name>");
    }

    @Override
    public void onExecute(CommandSender sender, String[] args, ReMinions plugin) {
        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            plugin.getConfig0().sendMessage(sender, "command_player_not_found", "%player%", playerName);
            return;
        }

        PlayerMinions playerMinions = plugin.getPlayerManager().getById(target.getUniqueId());
        if (playerMinions == null) return;

        playerMinions.getMinionUnlockeds().clear();
        int newMax = plugin.getConfig0().getInt("settings.default_max_minions");
        playerMinions.setMaxMinions(newMax);

        plugin.getConfig0().sendMessage(sender, "reset_unique_success_admin",
                "%player%", playerName,
                "%max_minions%", String.valueOf(newMax));
        plugin.getConfig0().sendMessage(target, "reset_unique_success_player",
                "%max_minions%", String.valueOf(newMax));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        if (args.length != 1) return List.of();
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[0])).toList();
    }
}
