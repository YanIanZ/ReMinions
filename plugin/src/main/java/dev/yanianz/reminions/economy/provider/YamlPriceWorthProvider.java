package dev.yanianz.reminions.economy.provider;

import java.util.OptionalDouble;
import dev.yanianz.reminions.economy.WorthProvider;
import org.bukkit.inventory.ItemStack;

/**
 * Sentinel provider: never produces a value on its own. Returning empty here forces the
 * {@code WorthService} chain to terminate and use the {@code Product.getPrice()} fallback.
 */
public final class YamlPriceWorthProvider implements WorthProvider {
    @Override public String id() { return "yaml"; }
    @Override public boolean isAvailable() { return true; }
    @Override public OptionalDouble getWorth(ItemStack item) { return OptionalDouble.empty(); }
}
