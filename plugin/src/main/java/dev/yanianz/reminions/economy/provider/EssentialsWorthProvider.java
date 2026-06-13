package dev.yanianz.reminions.economy.provider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.OptionalDouble;
import dev.yanianz.reminions.economy.WorthProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Reads per-unit worth from EssentialsX's {@code worth.yml} via the {@code IEssentials.getWorth()}
 * service. Reflection-based to avoid a compile-time dependency on EssentialsX.
 *
 * <p>The Essentials Worth service signature is roughly:
 * {@code worth.getPrice(IEssentials, ItemStack) -> BigDecimal} (per stack), so we
 * normalise to per-unit by dividing by the stack amount.
 */
public final class EssentialsWorthProvider implements WorthProvider {

    private boolean resolved;
    private Plugin essentialsPlugin;
    private Object worthService;
    private Method getPriceMethod;

    @Override public String id() { return "essentials"; }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Essentials");
    }

    @Override
    public OptionalDouble getWorth(ItemStack item) {
        if (item == null) return OptionalDouble.empty();
        if (!this.resolve()) return OptionalDouble.empty();
        try {
            // Worth#getPrice(IEssentials, ItemStack) returns BigDecimal (stack-total).
            ItemStack single = item.clone();
            single.setAmount(1);
            Object res = this.getPriceMethod.invoke(this.worthService, this.essentialsPlugin, single);
            if (!(res instanceof BigDecimal bd)) return OptionalDouble.empty();
            double v = bd.doubleValue();
            return v > 0 ? OptionalDouble.of(v) : OptionalDouble.empty();
        } catch (Throwable t) {
            return OptionalDouble.empty();
        }
    }

    private boolean resolve() {
        if (this.resolved) return this.worthService != null;
        this.resolved = true;
        try {
            this.essentialsPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
            if (this.essentialsPlugin == null) return false;
            Method getWorth = this.essentialsPlugin.getClass().getMethod("getWorth");
            this.worthService = getWorth.invoke(this.essentialsPlugin);
            if (this.worthService == null) return false;
            Class<?> iEssentialsClass = Class.forName("com.earth2me.essentials.IEssentials");
            this.getPriceMethod = this.worthService.getClass().getMethod("getPrice", iEssentialsClass, ItemStack.class);
            return true;
        } catch (Throwable t) {
            DebugLogger.warn("[Worth] Essentials Worth API not accessible: " + t.getMessage());
            this.worthService = null;
            return false;
        }
    }
}
