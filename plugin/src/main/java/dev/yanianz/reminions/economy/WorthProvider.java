package dev.yanianz.reminions.economy;

import java.util.OptionalDouble;
import org.bukkit.inventory.ItemStack;

/**
 * A resolver that looks up a single-unit sell price for an item from an external source
 * (Essentials worth.yml, ShopGUI+, EconomyShopGUI, the YAML 'price' field, etc.).
 */
public interface WorthProvider {

    /** Unique identifier used in config (e.g. "shopguiplus", "essentials", "yaml"). */
    String id();

    /** Returns true when the backing plugin/source is currently available. */
    boolean isAvailable();

    /** Returns per-unit worth for the item, or empty if this provider has no price for it. */
    OptionalDouble getWorth(ItemStack item);
}
