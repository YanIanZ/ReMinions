package dev.yanianz.reminions.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionRewardConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionModifierData;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.minion.MinionType;
import dev.yanianz.reminions.core.modifier.AutoSellPricing;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.core.product.Product;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for ReMinions. Placeholder keys are documented in the README.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>Server-wide stats (no player needed)</li>
 *   <li>Per-player stats</li>
 *   <li>Per-minion index lookups: {@code player_minion_index_<n>_<field>}</li>
 *   <li>Type/category counters: {@code player_minion_count_<type>}</li>
 * </ol>
 */
public final class ReMinionsExpansion extends PlaceholderExpansion {

    private final ReMinions plugin;
    private final RecentProductionTracker recentProduction;

    public ReMinionsExpansion(ReMinions plugin, RecentProductionTracker recentProduction) {
        this.plugin = plugin;
        this.recentProduction = recentProduction;
    }

    @NotNull @Override public String getIdentifier() { return "reminions"; }
    @NotNull @Override public String getAuthor()     { return "YanIanZ"; }
    @NotNull @Override public String getVersion()    { return "1.0.0"; }
    @Override public boolean persist()               { return true; }

    @Nullable
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase();

        // ── Server-wide placeholders (no player data needed) ──────────────────
        String serverValue = this.resolveServerPlaceholder(key);
        if (serverValue != null) return serverValue;

        // ── Per-player placeholders ───────────────────────────────────────────
        if (player == null || player.getUniqueId() == null) return "";
        PlayerMinions pm = this.plugin.getPlayerManager().getById(player.getUniqueId());
        if (pm == null) return "";

        String playerValue = this.resolvePlayerPlaceholder(key, pm, player);
        return playerValue != null ? playerValue : "";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Server-wide
    // ─────────────────────────────────────────────────────────────────────────

    private @Nullable String resolveServerPlaceholder(String key) {
        return switch (key) {
            case "server_total_minions"        -> String.valueOf(this.plugin.getPlayerManager().getAllMinions().size());
            case "server_total_players"        -> String.valueOf(this.plugin.getPlayerManager().getPlayersSnapshot().size());
            case "server_total_minion_configs" -> String.valueOf(this.plugin.getMinionManager().getAll().size());
            case "server_total_modifier_configs" -> String.valueOf(this.plugin.getModifierManager().getAll().size());
            case "server_total_skin_configs"   -> String.valueOf(this.plugin.getSkinManager().getAll().size());
            case "server_total_storage_configs"-> String.valueOf(this.plugin.getStorageManager().getAll().size());
            default                            -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-player
    // ─────────────────────────────────────────────────────────────────────────

    private @Nullable String resolvePlayerPlaceholder(String key, PlayerMinions pm, OfflinePlayer player) {
        switch (key) {
            // Slot info
            case "player_current_minions"   -> { return String.valueOf(pm.getCurrentMinions()); }
            case "player_max_minions"       -> { return String.valueOf(pm.getMaxMinions()); }
            case "player_slots_available"   -> { return String.valueOf(pm.getMaxMinions() - pm.getCurrentMinions()); }
            case "player_slots_percent"     -> {
                if (pm.getMaxMinions() == 0) return "0%";
                int pct = pm.getCurrentMinions() * 100 / pm.getMaxMinions();
                return pct + "%";
            }

            // Unique + reward progress
            case "player_unique_minions"        -> { return String.valueOf(pm.getTotalUniqueMinions()); }
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
            case "player_total_earnings"            -> { return String.valueOf(pm.getTotalEarnings()); }
            case "player_total_earnings_formatted"  -> { return formatMoney(pm.getTotalEarnings()); }

            // Aggregate stats across all minions owned by the player.
            case "player_total_collected"     -> { return String.valueOf(this.sumCollected(pm)); }
            case "player_total_modifiers"     -> { return String.valueOf(this.sumModifiers(pm)); }
            case "player_total_storage_used"  -> { return String.valueOf(this.sumStorageUsed(pm)); }
            case "player_total_storage_max"   -> { return String.valueOf(this.sumStorageMax(pm)); }
            case "player_total_storage_percent" -> {
                long used = this.sumStorageUsed(pm);
                long max  = this.sumStorageMax(pm);
                if (max == 0) return "0%";
                return String.format("%.1f%%", used * 100.0 / max);
            }
            case "player_total_active_minions" -> {
                long active = pm.getMinions().stream()
                        .filter(m -> m.getStatus() != null
                                && !m.getStatus().name().startsWith("POSITION_INVALID")
                                && !m.getStatus().name().equals("FULLY"))
                        .count();
                return String.valueOf(active);
            }
            // ── Time-based stats ────────────────────────────────────────────
            case "player_recent_production_hourly" -> {
                return String.valueOf(this.recentProduction.countLastHour(player.getUniqueId()));
            }
            case "player_recent_production_daily" -> {
                // Rolling hour × 24 is the cheapest reasonable approximation that doesn't
                // require us to keep a 24h-wide bucket array per player in memory.
                return String.valueOf(this.recentProduction.countLastHour(player.getUniqueId()) * 24L);
            }
            // ── Production estimates (expected items per hour across all minions) ──
            case "player_estimated_items_per_hour" -> {
                return String.valueOf(Math.round(this.estimatedItemsPerHour(pm)));
            }
            case "player_estimated_items_per_day" -> {
                return String.valueOf(Math.round(this.estimatedItemsPerHour(pm) * 24.0));
            }
            default -> { /* fall through */ }
        }

        // ── Dynamic lookups ──────────────────────────────────────────────────
        if (key.startsWith("player_minion_count_")) {
            return resolveTypeCount(key.substring("player_minion_count_".length()), pm);
        }
        if (key.startsWith("player_minion_index_")) {
            return resolveIndexPlaceholder(key, pm, player);
        }
        if (key.startsWith("player_minion_id_count_")) {
            String configId = key.substring("player_minion_id_count_".length()).toLowerCase();
            long count = pm.getMinions().stream().filter(m -> configId.equals(m.getName())).count();
            return String.valueOf(count);
        }
        // Per-modifier-type count on a specific minion:
        //   %reminions_player_minion_index_<n>_modifier_<TYPE>_count%
        if (key.startsWith("player_minion_index_") && key.endsWith("_count")) {
            // Parsed inline so the index resolver below keeps the simple <field> path. We only
            // hit this branch when the suffix really is `_count`.
            String body = key.substring("player_minion_index_".length(), key.length() - "_count".length());
            int sep = body.indexOf("_modifier_");
            if (sep >= 0) {
                try {
                    int idx = Integer.parseInt(body.substring(0, sep));
                    String typeName = body.substring(sep + "_modifier_".length()).toUpperCase();
                    Minion minion = pm.getMinionByIndex(idx);
                    if (minion == null) return "0";
                    ModifierType type;
                    try {
                        type = ModifierType.valueOf(typeName);
                    } catch (IllegalArgumentException invalid) {
                        return "0";
                    }
                    long count = minion.getModifiers().stream()
                            .filter(m -> m.getType() == type)
                            .filter(m -> !m.isExpired())
                            .count();
                    return String.valueOf(count);
                } catch (NumberFormatException ignored) {
                    return "0";
                }
            }
        }

        return null;
    }

    /**
     * Expected items-per-hour rate aggregated over every minion the player owns. Uses each
     * product's expected drop amount × the chance, divided by the minion's production speed
     * (seconds per action), so the result reflects both base output and any speed modifiers
     * applied to the minion at the moment the placeholder fires.
     */
    private double estimatedItemsPerHour(PlayerMinions pm) {
        double total = 0.0;
        for (Minion minion : pm.getMinions()) {
            MinionConfig cfg = this.plugin.getMinionManager().get(minion.getName());
            if (cfg == null) continue;
            double secondsPerAction = Math.max(1.0, minion.getProductionSpeed());
            double actionsPerHour = 3600.0 / secondsPerAction;
            double perAction = 0.0;
            for (Product p : cfg.products()) {
                perAction += p.getExpectedAmount();
            }
            total += actionsPerHour * perAction;
        }
        return total;
    }

    private static @Nullable String resolveTypeCount(String typeStr, PlayerMinions pm) {
        try {
            MinionType type = MinionType.valueOf(typeStr.toUpperCase());
            long count = pm.getMinions().stream().filter(m -> m.getType() == type).count();
            return String.valueOf(count);
        } catch (IllegalArgumentException ignored) {
            return "0";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-minion lookups (player_minion_index_<n>_<field>)
    // ─────────────────────────────────────────────────────────────────────────

    private @Nullable String resolveIndexPlaceholder(String params, PlayerMinions pm, OfflinePlayer player) {
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
            case "status"          -> minion.getStatus() == null ? "" : minion.getStatus().name();
            case "id"              -> minion.getName();
            case "type"            -> minion.getType() != null ? minion.getType().name() : "";
            case "level"           -> String.valueOf(minion.getLevel());
            case "level_roman"     -> Text.toRomanLevel(minion.getLevel());
            case "owner"           -> player.getName() != null ? player.getName() : "";
            case "collected"       -> String.valueOf(minion.getCollected());
            case "production_speed" -> String.format("%.2f", minion.getProductionSpeed());
            case "base_radius"     -> String.valueOf(minion.getBaseRadius());
            case "modifier_count"  -> String.valueOf(minion.getModifiers().size());
            case "world"           -> minion.getLoc() == null ? "" : minion.getLoc().getWorldName();
            case "x"               -> minion.getLoc() == null ? "" : String.valueOf(minion.getLoc().getBlockX());
            case "y"               -> minion.getLoc() == null ? "" : String.valueOf(minion.getLoc().getBlockY());
            case "z"               -> minion.getLoc() == null ? "" : String.valueOf(minion.getLoc().getBlockZ());
            case "skin"            -> minion.getCurrentSkin() == null ? "" : minion.getCurrentSkin();
            case "storage_name"    -> {
                MinionStorage storage = minion.getStorage();
                yield storage == null ? "" : storage.name();
            }
            case "storage_used"    -> {
                MinionStorage storage = minion.getStorage();
                yield storage == null ? "0" : String.valueOf(storageItemCount(storage));
            }
            case "storage_max"     -> {
                MinionStorage storage = minion.getStorage();
                yield storage == null ? "0" : String.valueOf(storage.inventory().getMaxSlots());
            }
            case "storage_percent" -> {
                MinionStorage storage = minion.getStorage();
                if (storage == null || storage.inventory().getMaxSlots() == 0) yield "0%";
                yield String.format("%.1f%%",
                        storageItemCount(storage) * 100.0 / storage.inventory().getMaxSlots());
            }
            case "has_auto_sell"   -> String.valueOf(minion.getModifiersByAnyType(ModifierType.AUTO_SELL) != null);
            case "auto_sell_multiplier" -> {
                MinionModifierData mod = minion.getModifiersByAnyType(ModifierType.AUTO_SELL);
                yield String.format("%.2f", AutoSellPricing.effectiveMultiplier(mod));
            }
            case "auto_sell_total_sold" -> {
                MinionModifierData mod = minion.getModifiersByAnyType(ModifierType.AUTO_SELL);
                yield mod == null ? "0" : String.valueOf(mod.getSoldItems());
            }
            case "auto_sell_total_earned" -> {
                MinionModifierData mod = minion.getModifiersByAnyType(ModifierType.AUTO_SELL);
                yield mod == null ? "0" : String.format("%.2f", mod.getMoneyEarned());
            }
            case "display" -> {
                MinionConfig cfg = this.plugin.getMinionManager().get(minion.getName());
                yield cfg == null ? "" : cfg.name().replace("%roman_level%", Text.toRomanLevel(minion.getLevel()));
            }
            default -> "";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Aggregations
    // ─────────────────────────────────────────────────────────────────────────

    private long sumCollected(PlayerMinions pm) {
        long total = 0L;
        for (Minion minion : pm.getMinions()) total += minion.getCollected();
        return total;
    }

    private long sumModifiers(PlayerMinions pm) {
        long total = 0L;
        for (Minion minion : pm.getMinions()) total += minion.getModifiers().size();
        return total;
    }

    private long sumStorageUsed(PlayerMinions pm) {
        long total = 0L;
        for (Minion minion : pm.getMinions()) {
            MinionStorage storage = minion.getStorage();
            if (storage != null) total += storageItemCount(storage);
        }
        return total;
    }

    /** Total stacked items in {@code storage}. Walks the inventory snapshot once. */
    private static int storageItemCount(MinionStorage storage) {
        if (storage == null || storage.inventory() == null) return 0;
        int total = 0;
        for (MinionInventory.ItemData data : storage.inventory().getSnapshot()) {
            total += data.getAmount();
        }
        return total;
    }

    private long sumStorageMax(PlayerMinions pm) {
        long total = 0L;
        for (Minion minion : pm.getMinions()) {
            MinionStorage storage = minion.getStorage();
            if (storage != null) total += storage.inventory().getMaxSlots();
        }
        return total;
    }

    private static String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000);
        if (amount >= 1_000_000)     return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)         return String.format("%.1fK", amount / 1_000);
        return String.format("%.0f", amount);
    }
}
