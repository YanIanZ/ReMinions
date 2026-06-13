package dev.yanianz.reminions.booster.provider;

import java.lang.reflect.Method;
import java.util.UUID;
import dev.yanianz.reminions.booster.BoostKind;
import dev.yanianz.reminions.booster.BoosterProvider;
import org.bukkit.Bukkit;

/**
 * Reflection wrapper around "Boosters" (by Zrips). The plugin's API exposes
 * {@code com.bgsoftware.boosters.api.BoostersAPI#getMultiplier(UUID, String)}.
 * Note that Boosters is global-scope by default; the provider still passes a UUID
 * in case the user is on a per-player fork.
 */
public final class ZripsBoostersProvider implements BoosterProvider {

    private boolean resolved;
    private Method method;

    @Override public String id() { return "zrips_boosters"; }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Boosters");
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
        for (String klass : new String[]{
                "com.bgsoftware.boosters.api.BoostersAPI",
                "com.zrips.boosters.api.BoostersApi"}) {
            try {
                Class<?> api = Class.forName(klass);
                this.method = api.getMethod("getMultiplier", UUID.class, String.class);
                return this.method;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String mapKind(BoostKind kind) {
        return switch (kind) {
            case SPEED -> "speed";
            case PRODUCTION -> "production";
            case EXP -> "exp";
            case SELL_PRICE -> "sell";
        };
    }
}
