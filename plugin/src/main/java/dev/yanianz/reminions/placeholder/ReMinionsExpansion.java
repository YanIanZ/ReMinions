package dev.yanianz.reminions.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionRewardConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReMinionsExpansion extends PlaceholderExpansion {
    private final ReMinions plugin;

    public ReMinionsExpansion(ReMinions plugin) {
        this.plugin = plugin;
    }

    @NotNull @Override public String getIdentifier() { return "reminions"; }
    @NotNull @Override public String getAuthor()     { return "YanIanZ"; }
    @NotNull @Override public String getVersion()    { return "2.0.7"; }
    @Override public boolean persist()               { return true; }

    @Nullable
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) return "";
        PlayerMinions playerMinions = this.plugin.getPlayerManager().getById(player.getUniqueId());
        if (playerMinions == null) return "";

        switch (params) {
            case "player_unique_minions"  -> { return String.valueOf(playerMinions.getTotalUniqueMinions()); }
            case "player_current_minions" -> { return String.valueOf(playerMinions.getCurrentMinions()); }
            case "player_max_minions"     -> { return String.valueOf(playerMinions.getMaxMinions()); }
            case "player_total_earnings"  -> { return String.valueOf(playerMinions.getTotalEarnings()); }
            case "player_needed_unique_minions" -> {
                int total = playerMinions.getTotalUniqueMinions();
                MinionRewardConfig nextReward = this.plugin.getConfig0().getNextMinionReward(total);
                if (nextReward == null) return "MAX";
                return String.valueOf(Math.max(0, nextReward.requiredUniqueMinions() - total));
            }
            case "player_progress_next_reward" -> {
                int total = playerMinions.getTotalUniqueMinions();
                MinionRewardConfig nextReward = this.plugin.getConfig0().getNextMinionReward(total);
                if (nextReward == null) return "100%";
                double pct = total * 100.0 / nextReward.requiredUniqueMinions();
                return String.format("%.1f%%", Math.min(pct, 100.0));
            }
            default -> {
                if (!params.startsWith("player_minion_index_")) return "";
                String[] parts = params.split("player_minion_index_|_");
                if (parts.length != 3) return "";
                int index;
                try { index = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return ""; }
                Minion minion = playerMinions.getMinionByIndex(index);
                if (minion == null) return "";
                return switch (parts[2].toLowerCase()) {
                    case "status"  -> minion.getStatus().name();
                    case "id"      -> minion.getName();
                    case "owner"   -> player.getName() != null ? player.getName() : "";
                    case "display" -> {
                        MinionConfig cfg = this.plugin.getMinionManager().get(minion.getName());
                        yield cfg == null ? "" : cfg.name().replace("%roman_level%", Text.toRomanLevel(minion.getLevel()));
                    }
                    default -> "";
                };
            }
        }
    }
}
