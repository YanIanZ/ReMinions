package dev.yanianz.reminions.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionRewardConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionType;
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
    @NotNull @Override public String getVersion()    { return "1.0.0"; }
    @Override public boolean persist()               { return true; }

    @Nullable
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // ── Server-wide placeholders (no player data needed) ──────────────────
        if (params.equals("server_total_minions")) {
            return String.valueOf(this.plugin.getPlayerManager().getAllMinions().size());
        }

        // ── Per-player placeholders ───────────────────────────────────────────
        if (player == null || player.getUniqueId() == null) return "";
        PlayerMinions pm = this.plugin.getPlayerManager().getById(player.getUniqueId());
        if (pm == null) return "";

        switch (params) {
            // Slot info
            case "player_current_minions"  -> { return String.valueOf(pm.getCurrentMinions()); }
            case "player_max_minions"      -> { return String.valueOf(pm.getMaxMinions()); }
            case "player_slots_available"  -> { return String.valueOf(pm.getMaxMinions() - pm.getCurrentMinions()); }
            case "player_slots_percent"    -> {
                if (pm.getMaxMinions() == 0) return "0%";
                int pct = pm.getCurrentMinions() * 100 / pm.getMaxMinions();
                return pct + "%";
            }

            // Unique & reward progress
            case "player_unique_minions"   -> { return String.valueOf(pm.getTotalUniqueMinions()); }
            case "player_needed_unique_minions" -> {
                int total = pm.getTotalUniqueMinions();
                MinionRewardConfig next = this.plugin.getConfig0().getNextMinionReward(total);
                if (next == null) return "MAX";
                return String.valueOf(Math.max(0, next.requiredUniqueMinions() - total));
            }
            case "player_progress_next_reward" -> {
                int total = pm.getTotalUniqueMinions();
                MinionRewardConfig next = this.plugin.getConfig0().getNextMinionReward(total);
                if (next == null) return "100%";
                return String.format("%.1f%%", Math.min(total * 100.0 / next.requiredUniqueMinions(), 100.0));
            }

            // Earnings
            case "player_total_earnings"   -> { return String.valueOf(pm.getTotalEarnings()); }
            case "player_total_earnings_formatted" -> { return formatMoney(pm.getTotalEarnings()); }

            // Minion type counts
            default -> {
                // %reminions_player_minion_count_<type>%  (miner, killer, farmer, lumberjack, fisherman)
                if (params.startsWith("player_minion_count_")) {
                    String typeStr = params.substring("player_minion_count_".length()).toUpperCase();
                    try {
                        MinionType type = MinionType.valueOf(typeStr);
                        long count = pm.getMinions().stream()
                                .filter(m -> m.getType() == type)
                                .count();
                        return String.valueOf(count);
                    } catch (IllegalArgumentException ignored) {
                        return "0";
                    }
                }

                // %reminions_player_minion_index_<n>_<field>%
                if (params.startsWith("player_minion_index_")) {
                    return resolveIndexPlaceholder(params, pm, player);
                }

                return "";
            }
        }
    }

    private String resolveIndexPlaceholder(String params, PlayerMinions pm, OfflinePlayer player) {
        // Expected format: player_minion_index_<n>_<field>
        String withoutPrefix = params.substring("player_minion_index_".length());
        int sep = withoutPrefix.indexOf('_');
        if (sep < 0) return "";
        int index;
        try {
            index = Integer.parseInt(withoutPrefix.substring(0, sep));
        } catch (NumberFormatException e) {
            return "";
        }
        String field = withoutPrefix.substring(sep + 1).toLowerCase();
        Minion minion = pm.getMinionByIndex(index);
        if (minion == null) return "";

        return switch (field) {
            case "status"  -> minion.getStatus().name();
            case "id"      -> minion.getName();
            case "type"    -> minion.getType() != null ? minion.getType().name() : "";
            case "level"   -> String.valueOf(minion.getLevel());
            case "owner"   -> player.getName() != null ? player.getName() : "";
            case "display" -> {
                MinionConfig cfg = this.plugin.getMinionManager().get(minion.getName());
                yield cfg == null ? "" : cfg.name().replace("%roman_level%", Text.toRomanLevel(minion.getLevel()));
            }
            default -> "";
        };
    }

    private static String formatMoney(double amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)     return String.format("%.1fK", amount / 1_000);
        return String.format("%.0f", amount);
    }
}
