package dev.yanianz.reminions.booster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.yanianz.reminions.booster.provider.PrisonBoostersProvider;
import dev.yanianz.reminions.booster.provider.ZripsBoostersProvider;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.utils.DebugLogger;

/**
 * Aggregates external-plugin booster multipliers into a single number per player + kind.
 * Multipliers from each available provider are multiplied together — a player with a 2× from
 * provider A and a 1.5× from provider B ends up with a 3× total.
 */
public final class BoosterService {

    private final Map<String, BoosterProvider> registry = new LinkedHashMap<>();
    private List<BoosterProvider> chain = List.of();

    public BoosterService() {
        this.register(new PrisonBoostersProvider());
        this.register(new ZripsBoostersProvider());
    }

    private void register(BoosterProvider provider) {
        this.registry.put(provider.id(), provider);
    }

    /** Rebuilds the active provider list from the {@code boosters.providers} config list. */
    public void reload(Config config) {
        List<String> ids = config.getStringList("boosters.providers");
        if (ids == null || ids.isEmpty()) {
            ids = List.of("prisonboosters", "zrips_boosters");
        }
        List<BoosterProvider> built = new ArrayList<>(ids.size());
        for (String id : ids) {
            BoosterProvider provider = this.registry.get(id.toLowerCase());
            if (provider == null) {
                DebugLogger.warn("[Boosters] Unknown provider id in config: " + id);
                continue;
            }
            built.add(provider);
        }
        this.chain = List.copyOf(built);
        DebugLogger.info("[Boosters] Provider chain: " + ids);
    }

    /** Returns the aggregated multiplier for {@code player} + {@code kind}; {@code 1.0} when nothing applies. */
    public double multiplier(UUID player, BoostKind kind) {
        if (player == null || this.chain.isEmpty()) return 1.0;
        double mult = 1.0;
        for (BoosterProvider provider : this.chain) {
            if (!provider.isAvailable()) continue;
            try {
                double v = provider.getMultiplier(player, kind);
                if (v > 0 && v != 1.0) mult *= v;
            } catch (Throwable ignored) {
                // Defensive: providers should already swallow their own errors.
            }
        }
        return mult;
    }
}
