package dev.yanianz.reminions.booster.provider;

import java.lang.reflect.Method;
import java.util.UUID;
import dev.yanianz.reminions.booster.BoostKind;
import dev.yanianz.reminions.booster.BoosterProvider;
import org.bukkit.Bukkit;

/**
 * Reflection wrapper around the "PrisonBoosters" plugin's static API.
 * The PrisonBoosters API exposes {@code getMultiplier(UUID, String)} where the second
 * parameter is the boost category (e.g. "SELL", "EXP", "SPEED"). This provider maps
 * ReMinions {@link BoostKind} values to those category strings.
 */
public final class PrisonBoostersProvider implements BoosterProvider {

    private boolean resolved;
    private Method method;

    @Override public String id() { return "prisonboosters"; }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("PrisonBoosters");
    }

    @Override
    public double getMultiplier(UUID player, BoostKind kind) {
        Method m = this.lookup();
        if (m == null) return 1.0;
        try {
            Object res = m.invoke(null, player, mapKind(kind));
            return res instanceof Number n ? Math.max(1.0, n.doubleValue()) : 1.0;
        } catch (Throwable t) {
            return 1.0;
        }
    }

    private Method lookup() {
        if (this.resolved) return this.method;
        this.resolved = true;
        try {
            Class<?> api = Class.forName("me.prisonboosters.api.PrisonBoostersApi");
            this.method = api.getMethod("getMultiplier", UUID.class, String.class);
        } catch (Throwable ignored) {}
        return this.method;
    }

    private static String mapKind(BoostKind kind) {
        return switch (kind) {
            case SPEED -> "SPEED";
            case PRODUCTION -> "PRODUCTION";
            case EXP -> "EXP";
            case SELL_PRICE -> "SELL";
        };
    }
}
