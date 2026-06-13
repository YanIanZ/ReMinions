package dev.yanianz.reminions.economy.provider;

import java.lang.reflect.Method;
import java.util.OptionalDouble;
import dev.yanianz.reminions.economy.WorthProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Reads sell price from EconomyShopGUI (free or premium) via its static
 * {@code EconomyShopGUIHook} API. Reflection-based so the plugin compiles without it.
 *
 * <p>Supports both:
 * <ul>
 *   <li>{@code me.gypopo.economyshopgui.api.EconomyShopGUIHook#getItemSellPrice(ShopItem, ItemStack)} — but we
 *       want a generic lookup, so we use {@code getItemStackPriceSell(ItemStack)} if present.</li>
 *   <li>Falls back to {@code getSellPrice(ItemStack)}.</li>
 * </ul>
 */
public final class EconomyShopGuiWorthProvider implements WorthProvider {

    private boolean resolved;
    private Method method;

    @Override public String id() { return "economyshopgui"; }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI")
                || Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI-Premium");
    }

    @Override
    public OptionalDouble getWorth(ItemStack item) {
        if (item == null) return OptionalDouble.empty();
        Method m = this.lookup();
        if (m == null) return OptionalDouble.empty();
        try {
            Object res = m.invoke(null, item);
            if (!(res instanceof Number n)) return OptionalDouble.empty();
            double price = n.doubleValue();
            return price > 0 ? OptionalDouble.of(price) : OptionalDouble.empty();
        } catch (Throwable t) {
            return OptionalDouble.empty();
        }
    }

    private Method lookup() {
        if (this.resolved) return this.method;
        this.resolved = true;
        try {
            Class<?> hook = Class.forName("me.gypopo.economyshopgui.api.EconomyShopGUIHook");
            // Prefer item-only variant if present
            for (String candidate : new String[]{"getItemSellPrice", "getItemStackPriceSell", "getSellPrice"}) {
                try {
                    this.method = hook.getMethod(candidate, ItemStack.class);
                    return this.method;
                } catch (NoSuchMethodException ignored) {}
            }
            DebugLogger.warn("[Worth] EconomyShopGUI API found but no compatible price method");
        } catch (Throwable t) {
            DebugLogger.warn("[Worth] EconomyShopGUI API not accessible: " + t.getMessage());
        }
        return this.method;
    }
}
