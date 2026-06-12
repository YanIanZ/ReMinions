package dev.yanianz.reminions.command.impl;

import java.util.List;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GetMinionCommand extends CommandHolder {

    public GetMinionCommand() {
        super("get", "minions.command.get", 5, "<player_name> <minion_id> <amount> <level> <notify>");
    }

    @Override
    public void onExecute(CommandSender sender, String[] args, ReMinions plugin) {
        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            plugin.getConfig0().sendMessage(sender, "command_player_not_found", "%player%", playerName);
            return;
        }

        MinionManager minionManager = plugin.getMinionManager();
        String minionId = args[1];
        MinionConfig minionConfig = minionManager.get(minionId);
        if (minionConfig == null) {
            plugin.getConfig0().sendMessage(sender, "minion_id_not_found", "%minion_id%", minionId);
            return;
        }

        int amount, level;
        try {
            amount = Math.max(1, Integer.parseInt(args[2]));
            level  = Math.max(1, Integer.parseInt(args[3]));
        } catch (NumberFormatException e) {
            plugin.getConfig0().sendMessage(sender, "invalid_number");
            return;
        }

        if (target.getInventory().firstEmpty() == -1) {
            plugin.getConfig0().sendMessage(sender, "inventory_full");
            return;
        }

        MinionUpgrade upgrade = minionConfig.getUpgrade(level);
        if (upgrade == null) {
            plugin.getConfig0().sendMessage(sender, "invalid_level_upgrade_minion", "%level%", level, "%minion_id%", minionId);
            return;
        }

        String skinId = minionConfig.getSkinLevel(level);
        MinionSkinConfig skin = skinId == null ? null : plugin.getSkinManager().get(skinId);
        if (skin == null) {
            plugin.getConfig0().sendMessage(sender, "invalid_skin_level_minion", "%level%", level, "%minion_id%", minionId);
            return;
        }

        ItemStack minionHead = minionConfig.getMinionHead(level, 0L, upgrade.maxStorage(), upgrade.productionSpeed());
        if (minionHead == null) return;
        minionHead.setAmount(amount);
        target.getInventory().addItem(minionHead);

        if (Boolean.parseBoolean(args[4])) {
            plugin.getConfig0().sendMessage(target, "received_minion",
                    "%minion_id%", minionId,
                    "%amount%", String.valueOf(amount),
                    "%roman_level%", Text.toRomanLevel(level));
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        return switch (args.length) {
            case 1 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[0])).toList();
            case 2 -> plugin.getMinionManager().keys().stream().filter(k -> k.startsWith(args[1])).toList();
            case 3, 4 -> Stream.of("1","2","3","4","5","6","7","8","9","10").filter(n -> n.startsWith(args[args.length - 1])).toList();
            case 5 -> Stream.of("true", "false").filter(s -> s.startsWith(args[4])).toList();
            default -> List.of();
        };
    }
}
