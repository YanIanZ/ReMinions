package dev.yanianz.reminions.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.core.product.Product;
import dev.yanianz.reminions.economy.provider.EconomyShopGuiWorthProvider;
import dev.yanianz.reminions.economy.provider.EssentialsWorthProvider;
import dev.yanianz.reminions.economy.provider.ShopGuiPlusWorthProvider;
import dev.yanianz.reminions.economy.provider.YamlPriceWorthProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves a sell price for a {@link Product}/{@link ItemStack} by walking through a
 * configured chain of {@link WorthProvider}s. The first provider that returns a non-empty
 * value wins; if none match, the YAML 'price' field on the product is used as a
 * last-resort fallback.
 */
public final class WorthService {

    private final Map<String, WorthProvider> registry = new LinkedHashMap<>();
    private List<WorthProvider> chain = List.of();

    public WorthService() {
        this.register(new ShopGuiPlusWorthProvider());
        this.register(new EconomyShopGuiWorthProvider());
        this.register(new EssentialsWorthProvider());
        this.register(new YamlPriceWorthProvider());
    }

    private void register(WorthProvider provider) {
        this.registry.put(provider.id(), provider);
    }

    /**
     * Rebuilds the resolver chain from the {@code worth.providers} list in {@code config.yml}.
     * Unknown ids are silently skipped after a debug warning.
     */
    public void reload(Config config) {
        List<String> ids = config.getStringList("worth.providers");
        if (ids == null || ids.isEmpty()) {
            ids = List.of("shopguiplus", "economyshopgui", "essentials", "yaml");
        }
        List<WorthProvider> built = new ArrayList<>(ids.size());
        for (String id : ids) {
            WorthProvider provider = this.registry.get(id.toLowerCase());
            if (provider == null) {
                DebugLogger.warn("[Worth] Unknown provider id in config: " + id);
                continue;
            }
            built.add(provider);
        }
        this.chain = List.copyOf(built);
        DebugLogger.info("[Worth] Resolver chain: " + ids);
    }

    /** Per-unit price for one {@code item}. Falls back to {@code product.getPrice()} when nothing matches. */
    public double resolve(Product product, ItemStack item) {
        for (WorthProvider provider : this.chain) {
            if (!provider.isAvailable()) continue;
            OptionalDouble v = provider.getWorth(item);
            if (v.isPresent()) return v.getAsDouble();
        }
        return product != null ? product.getPrice() : 0.0;
    }

    /** Convenience: total revenue for {@code amount} units. */
    public double resolveTotal(Product product, ItemStack item, int amount) {
        return this.resolve(product, item) * amount;
    }
}
