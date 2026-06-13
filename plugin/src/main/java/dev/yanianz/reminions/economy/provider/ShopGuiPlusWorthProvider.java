package dev.yanianz.reminions.economy.provider;

import java.lang.reflect.Method;
import java.util.OptionalDouble;
import dev.yanianz.reminions.economy.WorthProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Reads sell price from ShopGUI+'s static API via reflection so we don't need a compile-time
 * dependency. ShopGUI+ exposes {@code ShopGuiPlusApi#getItemStackPriceSell(ItemStack)}, which
 * returns the sell price (or -1 if the item isn't in any shop).
 */
public final class ShopGuiPlusWorthProvider implements WorthProvider {

    private boolean resolved;
    private Method method;

    @Override public String id() { return "shopguiplus"; }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ShopGUIPlus");
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
            return price >= 0 ? OptionalDouble.of(price) : OptionalDouble.empty();
        } catch (Throwable t) {
            return OptionalDouble.empty();
        }
    }

    private Method lookup() {
        if (this.resolved) return this.method;
        this.resolved = true;
        try {
            Class<?> api = Class.forName("net.brcdev.shopgui.ShopGuiPlusApi");
            this.method = api.getMethod("getItemStackPriceSell", ItemStack.class);
        } catch (Throwable t) {
            DebugLogger.warn("[Worth] ShopGUI+ API not accessible: " + t.getMessage());
        }
        return this.method;
    }
}
