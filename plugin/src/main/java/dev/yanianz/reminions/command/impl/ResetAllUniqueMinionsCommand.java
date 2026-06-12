package dev.yanianz.reminions.command.impl;

import java.util.List;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.command.CommandHolder;
import dev.yanianz.reminions.core.player.PlayerMinions;
import org.bukkit.command.CommandSender;

public class ResetAllUniqueMinionsCommand extends CommandHolder {

    public ResetAllUniqueMinionsCommand() {
        super("reset_unique_all", "minions.command.reset_unique_all", 0, "");
    }

    @Override
    public void onExecute(CommandSender sender, String[] args, ReMinions plugin) {
        List<PlayerMinions> allPlayers = plugin.getDatabase().getPlayerMinions();
        int defaultMax = plugin.getConfig0().getInt("settings.default_max_minions");
        int count = 0;

        for (PlayerMinions playerMinions : allPlayers) {
            playerMinions.getMinionUnlockeds().clear();
            playerMinions.setMaxMinions(defaultMax);
            count++;
        }

        plugin.getDatabase().savePlayersMinions(allPlayers);
        plugin.getConfig0().sendMessage(sender, "all_unique_minions_reset_success",
                "%count%", String.valueOf(count),
                "%default_max%", String.valueOf(defaultMax));
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args, ReMinions plugin) {
        return List.of();
    }
}
