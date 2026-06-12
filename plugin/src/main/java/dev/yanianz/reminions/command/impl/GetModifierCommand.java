package dev.yanianz.reminions.command.impl;

import java.util.List;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.config.ModifierConfig;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.utils.InventoryTransfer;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GetModifierCommand extends CommandHolder {

    public GetModifierCommand() {
        super("modifier", "minions.command.modifier", 4, "<player_name> <modifier_id> <amount> <notify>");
    }

    @Override
    public void onExecute(CommandSender sender, String[] args, ReMinions plugin) {
        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            plugin.getConfig0().sendMessage(sender, "command_player_not_found", "%player%", playerName);
            return;
        }

        String modifierId = args[1];
        ModifierConfig modifierConfig = plugin.getModifierManager().get(modifierId);
        if (modifierConfig == null) {
            plugin.getConfig0().sendMessage(sender, "modifier_id_not_found", "%modifier_id%", modifierId);
            return;
        }

        int amount;
        try {
            amount = Math.max(1, Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            plugin.getConfig0().sendMessage(sender, "invalid_number");
            return;
        }

        long durationMillis = modifierConfig.duration() < 0 ? -1L : modifierConfig.duration() * 1000L;
        String formattedDuration = Text.getFormattedDurationLeft(System.currentTimeMillis(), durationMillis);
        ItemStack modifierItem = modifierConfig.item()
                .toBuild(List.of(new ItemKey("modifier_duration", String.valueOf(durationMillis))),
                        "%duration%", formattedDuration, "%items_sold%", 0, "%money_earned%", 0);
        modifierItem.setAmount(amount);

        if (!InventoryTransfer.addAll(target.getInventory(), List.of(modifierItem))) {
            plugin.getConfig0().sendMessage(sender, "inventory_full");
            return;
        }

        if (Boolean.parseBoolean(args[3])) {
            plugin.getConfig0().sendMessage(target, "received_modifier",
                    "%modifier_name%", modifierConfig.displayName(),
                    "%modifier_category%", modifierConfig.category().name(),
                    "%duration%", formattedDuration,
                    "%items_sold%", 0,
                    "%money_earned%", 0);
        }
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        return switch (args.length) {
            case 1 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[0])).toList();
            case 2 -> plugin.getModifierManager().keys().stream().filter(k -> k.startsWith(args[1])).toList();
            case 3 -> Stream.of("1","2","3","4","5","6","7","8","9","10").filter(n -> n.startsWith(args[2])).toList();
            case 4 -> Stream.of("true", "false").filter(s -> s.startsWith(args[3])).toList();
            default -> List.of();
        };
    }
}
