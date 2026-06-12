package dev.yanianz.reminions.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import dev.yanianz.reminions.config.ModifierConfig;
import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionModifierData;
import dev.yanianz.reminions.core.modifier.ModifierCategory;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.core.product.Product;
import dev.yanianz.reminions.core.product.impl.CraftEngineProduct;
import dev.yanianz.reminions.core.product.impl.EcoItemProduct;
import dev.yanianz.reminions.core.product.impl.ItemsAdderProduct;
import dev.yanianz.reminions.core.product.impl.MMOItemProduct;
import dev.yanianz.reminions.core.product.impl.VanillaProduct;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.FileTreeHandler;
import org.bukkit.configuration.ConfigurationSection;

/** Loads modifier YAML configs and exposes helpers to query accumulated modifier values. */
public class ModifierManager extends FileTreeHandler<ModifierConfig> {

    private static final List<String> DEFAULT_MODIFIER_FILES = List.of(
            "enchanted_lava_bucket.yml", "orb_of_expansion_1.yml", "orb_of_expansion_2.yml",
            "orb_of_expansion_3.yml", "compactor.yml", "auto_sell.yml");

    public ModifierManager() {
        super(
                "modifiers",
                "modifiers",
                DEFAULT_MODIFIER_FILES,
                (path, id) -> {},
                (path, id) -> DebugLogger.debug(String.format("Modifier '%s' loaded successfully!", id)));
    }

    @Override
    public ModifierConfig load(ConfigurationSection section, String modifierId) {
        String displayName = section.getString("name", modifierId);
        String typeRaw     = section.getString("type", "SPEED");
        String categoryRaw = section.getString("category", "FUEL");

        ModifierType type;
        try {
            type = ModifierType.valueOf(typeRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            DebugLogger.warn("Invalid modifier type '" + typeRaw + "' for modifier '" + modifierId + "', defaulting to SPEED.");
            type = ModifierType.SPEED;
        }

        ModifierCategory category;
        try {
            category = ModifierCategory.valueOf(categoryRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            DebugLogger.warn("Invalid modifier category '" + categoryRaw + "' for modifier '" + modifierId + "', defaulting to FUEL.");
            category = ModifierCategory.FUEL;
        }

        double multiplier  = section.getDouble("multiplier", 1.0);
        int    duration    = section.getInt("duration", -1);
        boolean unbreakable = section.getBoolean("unbreakable");

        List<Product> upgradeProducts = loadUpgradeProducts(section.getConfigurationSection("upgrade_products"), modifierId);

        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection == null) {
            DebugLogger.warn("Missing 'item' section in modifier " + modifierId);
            return null;
        }
        ItemBuilder itemBuilder = ItemBuilder.buildToFile(null, itemSection).addKey("modifier_item", modifierId);
        return new ModifierConfig(modifierId, displayName, category, type, multiplier, upgradeProducts, itemBuilder, duration, unbreakable);
    }

    private List<Product> loadUpgradeProducts(ConfigurationSection productsSection, String modifierId) {
        List<Product> products = new ArrayList<>();
        if (productsSection == null) return products;

        for (String productKey : productsSection.getKeys(false)) {
            ConfigurationSection productSection = productsSection.getConfigurationSection(productKey);
            if (productSection == null) continue;

            String productType = productSection.getString("type", "vanilla").toLowerCase();
            double price = productSection.getDouble("price", 0.0);
            double chance = productSection.getDouble("chance", 1.0);
            String requiredProduct = productSection.getString("required_product");
            int requiredProductAmount = productSection.getInt("required_product_amount", 1);

            String amountRaw = productSection.getString("amount", "1");
            String[] amountParts = amountRaw.split("-");
            int amountMin, amountMax;
            if (amountParts.length == 2) {
                amountMin = Integer.parseInt(amountParts[0]);
                amountMax = Integer.parseInt(amountParts[1]);
            } else {
                int fixed = Integer.parseInt(amountParts[0]);
                amountMin = fixed;
                amountMax = fixed;
            }

            SourceExpConfig sourceExp = null;
            if (productSection.contains("source_exp")) {
                double exp   = productSection.getDouble("source_exp.exp");
                String skill = productSection.getString("source_exp.skill");
                sourceExp = new SourceExpConfig(skill, exp);
            }

            Product product = buildProduct(productKey, productType, productSection, modifierId,
                    price, amountMin, amountMax, chance, requiredProduct, requiredProductAmount, sourceExp);
            if (product != null) products.add(product);
        }
        return products;
    }

    private Product buildProduct(String key, String type, ConfigurationSection section, String modifierId,
                                 double price, int amountMin, int amountMax, double chance,
                                 String requiredProduct, int requiredProductAmount, SourceExpConfig sourceExp) {
        return switch (type) {
            case "vanilla" -> {
                ItemBuilder builder = ItemBuilder.buildToFile(key, section);
                yield new VanillaProduct(key, price, amountMin, amountMax, chance,
                        requiredProduct, requiredProductAmount, sourceExp, builder.toBuildNormal());
            }
            case "mmoitem" -> {
                String mmoId       = section.getString("id");
                String mmoCategory = section.getString("category");
                yield new MMOItemProduct(key, price, amountMin, amountMax, chance,
                        requiredProduct, requiredProductAmount, sourceExp, mmoId, mmoCategory);
            }
            case "ecoitem" -> {
                String ecoId = section.getString("id");
                yield new EcoItemProduct(key, price, amountMin, amountMax, chance,
                        requiredProduct, requiredProductAmount, sourceExp, ecoId);
            }
            case "craftengine" -> {
                String ceId = section.getString("id");
                yield new CraftEngineProduct(key, price, amountMin, amountMax, chance,
                        requiredProduct, requiredProductAmount, sourceExp, ceId);
            }
            case "itemsadder" -> {
                String iaId = section.getString("id");
                yield new ItemsAdderProduct(key, price, amountMin, amountMax, chance,
                        requiredProduct, requiredProductAmount, sourceExp, iaId);
            }
            default -> {
                DebugLogger.warn("Unknown product type '" + type + "' for modifier '" + modifierId + "'. Skipping.");
                yield null;
            }
        };
    }

    /** Sums the value of all modifiers of {@code type} currently applied to {@code minion}. */
    public double getModifierNumber(Minion minion, ModifierType type) {
        double total = 0.0;
        for (MinionModifierData modData : minion.getModifiers()) {
            ModifierConfig config = this.get(modData.getName());
            if (config != null && config.type() == type) {
                total += config.value();
            }
        }
        return total;
    }

    /** Returns all {@link ModifierConfig}s currently applied to the minion. */
    public Collection<ModifierConfig> getAllByType(List<MinionModifierData> modifiers) {
        List<ModifierConfig> result = new ArrayList<>();
        for (MinionModifierData modData : modifiers) {
            ModifierConfig config = this.get(modData.getName());
            if (config != null) result.add(config);
        }
        return result;
    }
}
