package dev.yanianz.reminions.command.impl;

import java.util.List;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.config.StorageConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GetStorageCommand extends CommandHolder {

    public GetStorageCommand() {
        super("storage", "minions.command.getstorage", 4, "<player_name> <storage_id> <amount> <notify>");
    }

    @Override
    public void onExecute(CommandSender sender, String[] args, ReMinions plugin) {
        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            plugin.getConfig0().sendMessage(sender, "command_player_not_found", "%player%", playerName);
            return;
        }

        String storageId = args[1];
        StorageConfig storageConfig = plugin.getStorageManager().get(storageId);
        if (storageConfig == null) {
            plugin.getConfig0().sendMessage(sender, "storage_id_not_found", "%storage_id%", storageId);
            return;
        }

        int amount;
        try {
            amount = Math.max(1, Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            plugin.getConfig0().sendMessage(sender, "invalid_number");
            return;
        }

        if (target.getInventory().firstEmpty() == -1) {
            plugin.getConfig0().sendMessage(sender, "inventory_full");
            return;
        }

        ItemStack storageItem = storageConfig.item().toBuild(
                "%storage%", storageConfig.maxStorage(),
                "%block_skin%", storageConfig.blockSkin().name());
        storageItem.setAmount(amount);
        target.getInventory().addItem(storageItem);

        if (Boolean.parseBoolean(args[3])) {
            plugin.getConfig0().sendMessage(target, "received_storage",
                    "%storage_id%", storageId,
                    "%amount%", String.valueOf(amount),
                    "%storage_name%", storageConfig.name());
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        return switch (args.length) {
            case 1 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[0])).toList();
            case 2 -> plugin.getStorageManager().keys().stream().filter(k -> k.startsWith(args[1])).toList();
            case 3 -> Stream.of("1","2","3","4","5","6","7","8","9","10").filter(n -> n.startsWith(args[2])).toList();
            case 4 -> Stream.of("true", "false").filter(s -> s.startsWith(args[3])).toList();
            default -> List.of();
        };
    }
}
