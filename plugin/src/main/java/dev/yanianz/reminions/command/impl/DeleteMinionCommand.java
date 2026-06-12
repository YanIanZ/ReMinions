package dev.yanianz.reminions.command.impl;

import java.util.List;
import java.util.UUID;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.player.PlayerMinions;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeleteMinionCommand extends CommandHolder {

    public DeleteMinionCommand() {
        super("delete", "minions.command.delete", 2, "<player_name> <minion_id>");
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

        try {
            UUID minionId = UUID.fromString(args[1]);
            Minion minion = playerMinions.getMinionById(minionId);
            if (minion == null) {
                plugin.getConfig0().sendMessage(sender, "minion_id_not_found", "%minion_id%", args[1]);
                return;
            }
            minion.despawn();
            plugin.getPlayerManager().removeMinion(playerMinions, minion);
            plugin.getConfig0().sendMessage(sender, "minion_deleted",
                    "%minion_name%", minion.getName(),
                    "%minion_id%", minion.getId().toString());
        } catch (Exception e) {
            plugin.getConfig0().sendMessage(sender, "minion_id_not_found", "%minion_id%", args[1]);
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        return switch (args.length) {
            case 1 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[0])).toList();
            case 2 -> {
                if (!(sender instanceof Player player)) yield List.of("empty");
                PlayerMinions pm = plugin.getPlayerManager().getById(player.getUniqueId());
                yield pm == null ? List.of("empty") : pm.getMinions().stream().map(m -> m.getId().toString()).toList();
            }
            default -> List.of();
        };
    }
}
