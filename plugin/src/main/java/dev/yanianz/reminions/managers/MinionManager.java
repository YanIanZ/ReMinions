package dev.yanianz.reminions.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import dev.yanianz.reminions.Keys;
import dev.yanianz.reminions.config.AnimationConfig;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.minion.MinionType;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.core.product.Product;
import dev.yanianz.reminions.core.product.impl.CraftEngineProduct;
import dev.yanianz.reminions.core.product.impl.EcoItemProduct;
import dev.yanianz.reminions.core.product.impl.ItemsAdderProduct;
import dev.yanianz.reminions.core.product.impl.MMOItemProduct;
import dev.yanianz.reminions.core.product.impl.VanillaProduct;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.FileTreeHandler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice.ExactChoice;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Loads {@code minions/**.yml} into immutable {@link MinionConfig} records and registers the
 * matching crafting recipes for every defined upgrade level.
 *
 * <p>This class is the YAML → domain-model bridge. The {@link #load} method is split into
 * small, named helpers so each section of the schema can be reasoned about (and changed)
 * independently:</p>
 *
 * <pre>
 *   load
 *    ├─ loadProducts          (products: {...})
 *    ├─ buildAnimationConfig  (properties.animations.{...})
 *    ├─ loadSkinLevels        (skin_levels: {...})
 *    ├─ loadUpgrades          (upgrades: {...})
 *    └─ registerUpgradeRecipes (synthesises Shaped recipes for each requirement shape)
 * </pre>
 */
public class MinionManager extends FileTreeHandler<MinionConfig> {

    /** Crafting grid is always 3×3 = 9 cells. */
    private static final int CRAFT_GRID_SIZE = 9;
    private static final int CRAFT_GRID_WIDTH = 3;

    private static final int DEFAULT_BASE_RADIUS = 4;
    private static final int DEFAULT_LOG_TREE_HEIGHT = 4;
    private static final double DEFAULT_DROP_CHANCE = 1.0;
    private static final int DEFAULT_REQUIRED_PRODUCT_AMOUNT = 1;

    public MinionManager() {
        super(
                "minions",
                "minions",
                List.of(
                        "mining/cobblestone_minion.yml",
                        "farming/wheat_minion.yml",
                        "lumberjack/jungle_minion.yml",
                        "combat/zombie_minion.yml",
                        "fisherman/squid_minion.yml"
                ),
                (file, key) -> {},
                (file, key) -> DebugLogger.debug(String.format("Minion '%s' loaded successfully!", key))
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Top-level loader
    // ─────────────────────────────────────────────────────────────────────────────

    public MinionConfig load(ConfigurationSection root, String minionId) {
        String name = root.getString("name", "&7Empty Name");
        MinionType type = MinionType.valueOf(root.getString("type", "MINER").toUpperCase());
        String categorySkin = root.getString("category_skin");

        ConfigurationSection properties = root.getConfigurationSection("properties");
        if (properties == null) {
            DebugLogger.warn("Missing 'properties' section in minion " + minionId);
            return null;
        }

        ConfigurationSection itemSection = root.getConfigurationSection("item");
        if (itemSection == null) {
            DebugLogger.warn("Missing 'item' section in minion " + minionId);
            return null;
        }
        ItemBuilder itemBuilder = ItemBuilder.buildToFile(minionId, itemSection);

        int baseRadius = properties.getInt("base_radius", DEFAULT_BASE_RADIUS);
        List<Product> products = this.loadProducts(root.getConfigurationSection("products"));

        String blockCheckAroundCsv = properties.getString("block_check_around");
        if (blockCheckAroundCsv == null) {
            DebugLogger.warn("Missing 'block_check_around' property in minion " + minionId);
            return null;
        }
        Set<Material> blocksCheckAround = Arrays.stream(blockCheckAroundCsv.split(";"))
                .map(Material::matchMaterial)
                .collect(Collectors.toSet());

        ConfigurationSection animations = properties.getConfigurationSection("animations");
        if (animations == null) {
            DebugLogger.warn("Missing 'animations' section in minion " + minionId);
            return null;
        }
        AnimationConfig animationConfig = this.buildAnimationConfig(type, animations, minionId);
        if (animationConfig == null) {
            return null;
        }

        Map<Integer, String> skinLevels = this.loadSkinLevels(root.getConfigurationSection("skin_levels"), minionId);
        Map<Integer, MinionUpgrade> upgrades = this.loadUpgrades(root.getConfigurationSection("upgrades"), minionId);

        MinionConfig config = new MinionConfig(minionId, name, type, itemBuilder, baseRadius,
                products, blocksCheckAround, animationConfig, skinLevels, upgrades, categorySkin);
        this.registerUpgradeRecipes(config, minionId);
        return config;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Products loader
    // ─────────────────────────────────────────────────────────────────────────────

    private List<Product> loadProducts(ConfigurationSection productsSection) {
        ArrayList<Product> products = new ArrayList<>();
        if (productsSection == null) {
            return products;
        }
        for (String productKey : productsSection.getKeys(false)) {
            ConfigurationSection productSection = productsSection.getConfigurationSection(productKey);
            if (productSection == null) continue;

            String typeStr = productSection.getString("type", "vanilla").toLowerCase();
            double chance = productSection.getDouble("chance", DEFAULT_DROP_CHANCE);
            String requiredProduct = productSection.getString("required_product");
            int requiredAmount = productSection.getInt("required_product_amount", DEFAULT_REQUIRED_PRODUCT_AMOUNT);
            int price = productSection.getInt("price", 0);

            int[] amountRange = this.parseAmountRange(productSection.getString("amount", "1"));
            int minAmount = amountRange[0];
            int maxAmount = amountRange[1];

            SourceExpConfig expConfig = null;
            if (productSection.contains("source_exp")) {
                double exp = productSection.getDouble("source_exp.exp");
                String skillId = productSection.getString("source_exp.skill");
                expConfig = new SourceExpConfig(skillId, exp);
            }

            Product product = this.buildProduct(typeStr, productKey, productSection, price, minAmount, maxAmount,
                    chance, requiredProduct, requiredAmount, expConfig);
            if (product != null) {
                products.add(product);
            }
        }
        return products;
    }

    private int[] parseAmountRange(String raw) {
        String[] parts = raw.split("-");
        if (parts.length == 2) {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        }
        int single = Integer.parseInt(parts[0]);
        return new int[]{single, single};
    }

    /** Switch over the product {@code type} string and instantiate the matching concrete {@link Product}. */
    private Product buildProduct(String typeStr, String productKey, ConfigurationSection productSection,
                                 int price, int minAmount, int maxAmount, double chance,
                                 String requiredProduct, int requiredAmount, SourceExpConfig expConfig) {
        return switch (typeStr) {
            case "vanilla" -> {
                ItemStack item = ItemBuilder.buildToFile(null, productSection).toBuild();
                yield new VanillaProduct(productKey, price, minAmount, maxAmount, chance,
                        requiredProduct, requiredAmount, expConfig, item);
            }
            case "mmoitem" -> new MMOItemProduct(productKey, price, minAmount, maxAmount, chance,
                    requiredProduct, requiredAmount, expConfig,
                    productSection.getString("id"), productSection.getString("category"));
            case "ecoitem" -> new EcoItemProduct(productKey, price, minAmount, maxAmount, chance,
                    requiredProduct, requiredAmount, expConfig, productSection.getString("id"));
            case "craftengine" -> new CraftEngineProduct(productKey, price, minAmount, maxAmount, chance,
                    requiredProduct, requiredAmount, expConfig, productSection.getString("id"));
            case "itemsadder" -> new ItemsAdderProduct(productKey, price, minAmount, maxAmount, chance,
                    requiredProduct, requiredAmount, expConfig, productSection.getString("id"));
            default -> null;
        };
    }

    /** Bare-minimum product used inside upgrade requirements (no price/range/chance). */
    private Product buildRequirementProduct(String typeStr, String productKey, ConfigurationSection productSection) {
        return switch (typeStr) {
            case "vanilla" -> new VanillaProduct(productKey, 0.0, 0, 0, 0.0, null, 0, null,
                    ItemBuilder.buildToFile(null, productSection).toBuild());
            case "mmoitem" -> new MMOItemProduct(productKey, 0.0, 0, 0, 0.0, null, 0, null,
                    productSection.getString("id"), productSection.getString("category"));
            case "ecoitem" -> new EcoItemProduct(productKey, 0.0, 0, 0, 0.0, null, 0, null,
                    productSection.getString("id"));
            case "craftengine" -> new CraftEngineProduct(productKey, 0.0, 0, 0, 0.0, null, 0, null,
                    productSection.getString("id"));
            case "itemsadder" -> new ItemsAdderProduct(productKey, 0.0, 0, 0, 0.0, null, 0, null,
                    productSection.getString("id"));
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Animation builder (one branch per minion type)
    // ─────────────────────────────────────────────────────────────────────────────

    private AnimationConfig buildAnimationConfig(MinionType type, ConfigurationSection anim, String minionId) {
        return switch (type) {
            case MINER -> {
                Material block = Material.matchMaterial(anim.getString("block", "STONE"));
                if (block == null || !block.isBlock()) {
                    DebugLogger.warn("Invalid block '" + anim.getString("block") + "' for MINER animation in minion " + minionId);
                    yield null;
                }
                yield new AnimationConfig(block);
            }
            case FARMER -> {
                Material crop = Material.matchMaterial(anim.getString("crop", "CARROTS").toUpperCase());
                if (crop == null || !crop.isBlock()) {
                    DebugLogger.warn("Invalid crop '" + anim.getString("crop") + "' for FARMER animation in minion " + minionId);
                    yield null;
                }
                Particle particle = this.parseParticleOrFallback(anim.getString("crop_particle", "HAPPY_VILLAGER"),
                        "FARMER", minionId, NMSHandlerProvider.getParticleBridge().cropGrowParticle(), "HAPPY_VILLAGER");
                yield new AnimationConfig(crop, particle);
            }
            case LUMBERJACK -> {
                Material sapling = Material.matchMaterial(anim.getString("before_place_tree", "JUNGLE_SAPLING").toUpperCase());
                Material leaves  = Material.matchMaterial(anim.getString("leave_tree", "JUNGLE_LEAVES").toUpperCase());
                Material log     = Material.matchMaterial(anim.getString("log_tree", "JUNGLE_LOG").toUpperCase());
                Material fruit   = Material.matchMaterial(anim.getString("fruit_tree", "COCOA").toUpperCase());
                int logHeight    = anim.getInt("log_tree_height", DEFAULT_LOG_TREE_HEIGHT);
                if (sapling == null || leaves == null || log == null || fruit == null) {
                    DebugLogger.warn("Invalid tree materials for LUMBERJACK animation in minion " + minionId);
                    yield null;
                }
                yield new AnimationConfig(sapling, leaves, log, fruit, logHeight);
            }
            case KILLER -> {
                EntityType entity = EntityType.valueOf(anim.getString("entity", "ZOMBIE").toUpperCase());
                Particle particle = this.parseParticleOrFallback(anim.getString("entity_particle", "HAPPY_VILLAGER"),
                        "KILLER", minionId, NMSHandlerProvider.getParticleBridge().entityParticle(), "ANGRY_VILLAGER");
                yield new AnimationConfig(entity, particle);
            }
            case FISHERMAN -> {
                EntityType entity = EntityType.valueOf(anim.getString("entity_catch", "ZOMBIE").toUpperCase());
                Particle particle = this.parseParticleOrFallback(anim.getString("entity_particle_catch", "BUBBLE_POP"),
                        "FISHERMAN", minionId, NMSHandlerProvider.getParticleBridge().bubbleParticle(), "BUBBLE_POP");
                yield new AnimationConfig(entity, particle, true);
            }
            default -> {
                DebugLogger.warn("Unknown minion type: " + type + " for minion " + minionId);
                yield null;
            }
        };
    }

    private Particle parseParticleOrFallback(String name, String sectionLabel, String minionId,
                                             Particle fallback, String fallbackName) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (NoSuchFieldError | IllegalArgumentException ignored) {
            DebugLogger.warn("Invalid particle '" + name + "' for " + sectionLabel
                    + " animation in minion " + minionId + ". Defaulting to " + fallbackName + ".");
            return fallback;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Skin levels + upgrades
    // ─────────────────────────────────────────────────────────────────────────────

    private Map<Integer, String> loadSkinLevels(ConfigurationSection section, String minionId) {
        HashMap<Integer, String> result = new HashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                String path = section.getString(key + ".path", "");
                if (!path.isEmpty()) {
                    result.put(level, path);
                }
            } catch (NumberFormatException ignored) {
                DebugLogger.warn("Invalid skin level '" + key + "' in minion " + minionId);
            }
        }
        return result;
    }

    private Map<Integer, MinionUpgrade> loadUpgrades(ConfigurationSection section, String minionId) {
        HashMap<Integer, MinionUpgrade> result = new HashMap<>();
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                ConfigurationSection upgradeSection = section.getConfigurationSection(key);
                if (upgradeSection == null) continue;

                double productionSpeed = upgradeSection.getDouble("production_speed", -1.0);
                int radius = upgradeSection.getInt("radius", DEFAULT_BASE_RADIUS);
                int maxStorage = upgradeSection.getInt("max_storage", -1);

                MinionUpgrade.Requirement requirement =
                        this.loadRequirement(upgradeSection.getConfigurationSection("requirement"), key, minionId);
                result.put(level, new MinionUpgrade(key, minionId, level, productionSpeed, radius, maxStorage, requirement));
            } catch (NumberFormatException ignored) {
                DebugLogger.warn("Invalid upgrade key '" + key + "' in minion " + minionId);
            }
        }
        return result;
    }

    private MinionUpgrade.Requirement loadRequirement(ConfigurationSection requirementSection,
                                                      String upgradeKey, String minionId) {
        if (requirementSection == null) return null;

        HashMap<Character, MinionUpgrade.ItemRequirement> requirementBySymbol = new HashMap<>();
        ConfigurationSection itemsSection = requirementSection.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemKey : itemsSection.getKeys(false)) {
                this.parseRequirementItem(itemKey, itemsSection.getConfigurationSection(itemKey),
                        requirementBySymbol, upgradeKey, minionId);
            }
        }

        List<String> shapeLines = requirementSection.getStringList("shape");
        String flat = String.join("", shapeLines);
        char[] shape = flat.toCharArray();
        if (shape.length != CRAFT_GRID_SIZE) {
            DebugLogger.warn("Invalid shape length in upgrade " + upgradeKey + " for minion " + minionId);
            return null;
        }
        return new MinionUpgrade.Requirement(requirementBySymbol, shape);
    }

    private void parseRequirementItem(String itemKey, ConfigurationSection itemSection,
                                      Map<Character, MinionUpgrade.ItemRequirement> requirementBySymbol,
                                      String upgradeKey, String minionId) {
        try {
            if (itemSection == null) return;
            String display = itemSection.getString("display", "&7Unknown");
            int amount = itemSection.getInt("amount", 1);
            ConfigurationSection productSection = itemSection.getConfigurationSection("product");
            if (productSection == null) return;

            String type = productSection.getString("type", "vanilla").toLowerCase();
            Product product = this.buildRequirementProduct(type, itemKey, productSection);
            if (product != null) {
                requirementBySymbol.put(itemKey.charAt(0),
                        new MinionUpgrade.ItemRequirement(product, display, amount));
            }
        } catch (NumberFormatException ignored) {
            DebugLogger.warn("Invalid item index '" + itemKey + "' in upgrade " + upgradeKey + " for minion " + minionId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Recipe synthesis (one shaped recipe per upgrade level that has a requirement)
    // ─────────────────────────────────────────────────────────────────────────────

    private void registerUpgradeRecipes(MinionConfig config, String minionId) {
        for (MinionUpgrade upgrade : config.upgrades().values()) {
            MinionUpgrade.Requirement requirement = upgrade.requirement();
            if (requirement == null || requirement.items().isEmpty()) continue;

            int level = upgrade.level();
            ItemStack resultHead = config.getMinionHead(level, 0L, upgrade.maxStorage(), upgrade.productionSpeed());
            if (resultHead == null) continue;

            NamespacedKey recipeKey = Keys.of(minionId + "_lvl" + level);
            DebugLogger.debug("📦 Loading recipe " + recipeKey.getKey() + " ...");

            char[] shape = requirement.shape();
            if (shape.length != CRAFT_GRID_SIZE) {
                DebugLogger.warn("❌ Invalid shape for minion " + minionId + " (expected 9, got " + shape.length + ")");
                continue;
            }
            String[] shapeRows = new String[]{
                    new String(shape, 0, CRAFT_GRID_WIDTH),
                    new String(shape, CRAFT_GRID_WIDTH, CRAFT_GRID_WIDTH),
                    new String(shape, CRAFT_GRID_WIDTH * 2, CRAFT_GRID_WIDTH)
            };

            ShapedRecipe recipe = new ShapedRecipe(recipeKey, resultHead);
            recipe.shape(shapeRows);

            Set<Character> usedSymbols = new HashSet<>();
            for (char c : shape) {
                if (c != ' ') usedSymbols.add(c);
            }
            // '?' = the previous-level minion item, validated separately by RecipeListener.
            if (usedSymbols.contains('?')) {
                recipe.setIngredient('?', new MaterialChoice(Material.PLAYER_HEAD));
            }
            for (Entry<Character, MinionUpgrade.ItemRequirement> entry : requirement.items().entrySet()) {
                char symbol = entry.getKey();
                if (symbol == ' ' || !usedSymbols.contains(symbol)) continue;
                MinionUpgrade.ItemRequirement itemRequirement = entry.getValue();
                ItemStack stack = itemRequirement.product().buildItem();
                if (stack == null) continue;
                stack.setAmount(itemRequirement.amount());
                recipe.setIngredient(symbol, new ExactChoice(stack));
            }
            RecipeManager.registerRecipe(recipe);
        }
    }
}
