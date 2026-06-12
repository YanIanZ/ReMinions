package dev.yanianz.reminions.core.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.yanianz.reminions.Keys;
import dev.yanianz.reminions.adapters.ItemStackAdapter;
import dev.yanianz.reminions.adapters.Location3fAdapter;
import dev.yanianz.reminions.managers.importer.old.MinionAdapterOld;
import dev.yanianz.reminions.managers.importer.old.MinionOld;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.Location3f;
import dev.yanianz.reminions.utils.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Fluent {@link ItemStack} builder + YAML loader used by every menu/icon/recipe in the plugin.
 *
 * <p>Holds the abstract description of an item (material, lore, enchants, custom-model-data,
 * head texture, leather RGB, PDC keys) and turns it into a concrete {@link ItemStack} on
 * demand via {@link #toBuild}. The same builder can be reused multiple times — each
 * {@code toBuild} call yields an independent stack.</p>
 *
 * <p>{@link #buildToFile} is the YAML adapter: it reads a {@link ConfigurationSection} and
 * configures the builder accordingly. Used by every config that ships an "item" subsection
 * (menu icons, minion heads, modifier items, storage items, …).</p>
 */
public class ItemBuilder {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Location3f.class, new Location3fAdapter())
            .registerTypeAdapter(ItemStack.class, new ItemStackAdapter())
            .registerTypeAdapter(MinionOld.class, new MinionAdapterOld())
            .setPrettyPrinting()
            .create();

    // ─────────────────────────────────────────────────────────────────────────────
    // Well-known PDC key names referenced by other classes
    // ─────────────────────────────────────────────────────────────────────────────

    public static final String MINION_ITEM_KEY = "minion_item";
    public static final String MINION_MODIFIER_KEY = "modifier_item";
    public static final String MINION_MODIFIER_ID_KEY = "modifier_id";
    public static final String MINION_MODIFIER_DURATION_KEY = "modifier_duration";
    public static final String MINION_INVENTORY_ITEM_KEY = "minion_inventory_item";
    public static final String MINION_SKIN_KEY = "skin_item";
    public static final String STORAGE_KEY = "storage_item";
    public static final String STORAGE_ID_KEY = "storage_id";
    public static final String MINION_MENU_LEVEL = "minion_menu_level";

    /** Sentinel used by the YAML loader for "no custom-model-data". */
    private static final int CUSTOM_MODEL_DATA_UNSET = -1;

    private static final String SKULL_PROFILE_NAME = "Leo_S2";

    // ─────────────────────────────────────────────────────────────────────────────
    // Mutable state
    // ─────────────────────────────────────────────────────────────────────────────

    private final String id;
    private String material;
    private int amount = 1;
    private String name;
    private String headTexture;
    private int[] rgb;
    private final Map<Enchantment, Integer> enchants = new HashMap<>();
    private final Set<ItemFlag> flags = new HashSet<>();
    private final List<String> lore = new ArrayList<>();
    private final List<ItemKey> internalKeys = new ArrayList<>();
    private int customModelData;

    // ─────────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────────

    public ItemBuilder(String id, String material) {
        this.id = id;
        this.material = material;
    }

    /** Deep copy, including the {@code id}. */
    public ItemBuilder(ItemBuilder source) {
        this.id = source.id;
        this.material = source.material;
        this.amount = source.amount;
        this.name = source.name;
        this.headTexture = source.headTexture;
        this.customModelData = source.customModelData;
        this.rgb = source.rgb != null ? source.rgb.clone() : null;
        this.enchants.putAll(source.enchants);
        this.flags.addAll(source.flags);
        this.lore.addAll(source.lore);
        this.internalKeys.addAll(source.internalKeys);
    }

    /** Deep copy that replaces the {@code id}. */
    public ItemBuilder(String id, ItemBuilder source) {
        this.id = id;
        this.material = source.material;
        this.amount = source.amount;
        this.name = source.name;
        this.headTexture = source.headTexture;
        this.rgb = source.rgb != null ? source.rgb.clone() : null;
        this.customModelData = source.customModelData;
        this.enchants.putAll(source.enchants);
        this.flags.addAll(source.flags);
        this.lore.addAll(source.lore);
        this.internalKeys.addAll(source.internalKeys);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Fluent setters
    // ─────────────────────────────────────────────────────────────────────────────

    public ItemBuilder setMaterial(String material) { this.material = material; return this; }
    public ItemBuilder setMaterial(Material material) { this.material = material.name(); return this; }
    public ItemBuilder setAmount(int amount) { this.amount = amount; return this; }
    public ItemBuilder setName(String name) { this.name = name; return this; }
    public ItemBuilder addLine(String line) { this.lore.add(line); return this; }
    public ItemBuilder addLines(List<String> lines) { this.lore.addAll(lines); return this; }
    public ItemBuilder customModelData(int value) { this.customModelData = value; return this; }
    public ItemBuilder addEnchant(Enchantment ench, int level) { this.enchants.put(ench, level); return this; }
    public ItemBuilder addEnchants(Map<Enchantment, Integer> entries) { this.enchants.putAll(entries); return this; }
    public ItemBuilder addFlag(ItemFlag flag) { this.flags.add(flag); return this; }
    public ItemBuilder addFlags(ItemFlag... flagsToAdd) { this.flags.addAll(Arrays.asList(flagsToAdd)); return this; }
    public ItemBuilder setHeadTexture(String base64Texture) { this.headTexture = base64Texture; return this; }

    public ItemBuilder setRGB(int red, int green, int blue) {
        this.rgb = new int[]{red, green, blue};
        return this;
    }

    public ItemBuilder addKey(String key, Object value) {
        this.internalKeys.add(new ItemKey(key, value.toString()));
        return this;
    }

    public ItemBuilder addKeys(List<ItemKey> keys) {
        this.internalKeys.addAll(keys);
        return this;
    }

    /** Adds (or replaces) a single PDC entry by key. */
    public ItemBuilder setKey(String key, Object value) {
        this.internalKeys.removeIf(k -> k.key().equals(key));
        this.internalKeys.add(new ItemKey(key, value.toString()));
        return this;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Build → ItemStack
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Materialises an {@link ItemStack} from this builder.
     *
     * @param skipIdKey       if {@code true}, do not write the implicit {@code item_id} PDC entry
     *                        (used by menus that show the same icon many times with different keys)
     * @param extraKeys       PDC entries that override / add to the builder's internal keys
     * @param placeholderArgs key/value pairs forwarded to {@link Text#parseComponent} for name + lore
     */
    public ItemStack toBuild(boolean skipIdKey, List<ItemKey> extraKeys, Object... placeholderArgs) {
        Material parsedMaterial = Material.matchMaterial(this.material);
        if (parsedMaterial == null) {
            throw new IllegalArgumentException("Invalid material for item: " + this.material);
        }
        ItemStack stack = new ItemStack(parsedMaterial, this.amount);
        stack.editMeta(meta -> {
            if (this.name != null) {
                meta.displayName(Text.parseComponent(this.name, placeholderArgs));
            }
            if (!this.lore.isEmpty()) {
                meta.lore(Text.parseComponents(this.lore, placeholderArgs));
            }
            this.enchants.forEach((ench, level) -> meta.addEnchant(ench, level, true));
            if (!this.flags.isEmpty()) {
                meta.addItemFlags(this.flags.toArray(new ItemFlag[0]));
            }
            if (this.customModelData != CUSTOM_MODEL_DATA_UNSET) {
                CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
                cmd.setFloats(List.of((float) this.customModelData));
                meta.setCustomModelDataComponent(cmd);
            }
            if (meta instanceof SkullMeta skullMeta && this.headTexture != null) {
                applyHeadTexture(skullMeta, this.headTexture);
            }
            if (meta instanceof LeatherArmorMeta leatherMeta && this.rgb != null) {
                leatherMeta.setColor(Color.fromRGB(this.rgb[0], this.rgb[1], this.rgb[2]));
            }
            this.writePdcEntries(meta.getPersistentDataContainer(), skipIdKey, extraKeys);
        });
        return stack;
    }

    public ItemStack toBuild(List<ItemKey> extraKeys, Object... placeholderArgs) {
        return this.toBuild(false, extraKeys, placeholderArgs);
    }

    public ItemStack toBuild(Object... placeholderArgs) {
        return this.toBuild(false, List.of(), placeholderArgs);
    }

    /** Variant that skips the implicit {@code item_id} PDC entry — useful for filler icons. */
    public ItemStack toBuildNormal(Object... placeholderArgs) {
        return this.toBuild(true, List.of(), placeholderArgs);
    }

    private void writePdcEntries(PersistentDataContainer pdc, boolean skipIdKey, List<ItemKey> extraKeys) {
        if (this.id != null && !skipIdKey) {
            pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, this.id);
        }
        extraKeys.forEach(k -> pdc.set(Keys.of(k.key()), PersistentDataType.STRING, k.value()));
        this.internalKeys.forEach(k -> pdc.set(Keys.of(k.key()), PersistentDataType.STRING, k.value()));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Static helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /** Stamps a skull meta with a base64 textures property. */
    public static void applyHeadTexture(SkullMeta meta, String base64Texture) {
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), SKULL_PROFILE_NAME);
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);
    }

    /** Reads a PDC entry on an existing item stack. Returns {@code null} when absent. */
    public static ItemKey getPersistentKey(ItemStack stack, String keyName) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey namespacedKey = Keys.of(keyName);
        String value = pdc.get(namespacedKey, PersistentDataType.STRING);
        return value == null ? null : new ItemKey(keyName, value);
    }

    public ItemKey getItemKey(String keyName) {
        return this.internalKeys.stream().filter(k -> k.key().equals(keyName)).findFirst().orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // YAML loader
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Builds an {@link ItemBuilder} from a YAML section. Schema:
     *
     * <pre>
     * material:           "DIAMOND_SWORD"       (required)
     * name:               "&aSharp Sword"        (optional)
     * head_texture:       "&lt;base64&gt;"          (optional, skull only)
     * lore:               ["&7Line 1", ...]      (optional)
     * custom_model_data:  42                     (optional, integer)
     * rgb:                "255,128,0" | "#FF8000" | "&c"   (optional, leather only)
     * flags:              ["HIDE_ATTRIBUTES"]    (optional)
     * enchants:           ["sharpness:5"]        (optional)
     * data:               ["my_key:my_value"]    (optional, PDC entries)
     * </pre>
     */
    public static ItemBuilder buildToFile(String id, ConfigurationSection section) {
        String material = section.getString("material", "STONE");
        String name = section.getString("name", null);
        String headTexture = section.getString("head_texture", null);
        List<String> lore = section.getStringList("lore");
        int customModelData = section.getInt("custom_model_data", CUSTOM_MODEL_DATA_UNSET);

        ItemBuilder builder = new ItemBuilder(id, material)
                .setName(name)
                .addLines(lore)
                .customModelData(customModelData)
                .setHeadTexture(headTexture);

        String rgbExpr = section.getString("rgb", null);
        if (rgbExpr != null) {
            int[] rgb = parseColor(rgbExpr);
            if (rgb != null) {
                builder.setRGB(rgb[0], rgb[1], rgb[2]);
            }
        }
        applyFlags(builder, section.getStringList("flags"), id);
        applyEnchants(builder, section.getStringList("enchants"), id);
        applyDataKeys(builder, section.getStringList("data"), id);
        return builder;
    }

    private static void applyFlags(ItemBuilder builder, List<String> flagNames, String itemId) {
        for (String flagName : flagNames) {
            try {
                builder.addFlag(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                DebugLogger.warn("Invalid flag '" + flagName + "' in item " + itemId);
            }
        }
    }

    private static void applyEnchants(ItemBuilder builder, List<String> enchantExpressions, String itemId) {
        for (String enchantExpr : enchantExpressions) {
            try {
                String[] parts = enchantExpr.split(":");
                if (parts.length != 2) continue;
                String enchantName = parts[0].toLowerCase();
                int level = Integer.parseInt(parts[1]);
                Enchantment enchantment = NMSHandlerProvider.getItemBridge().applyEnchant(enchantName);
                if (enchantment != null) {
                    builder.addEnchant(enchantment, level);
                } else {
                    DebugLogger.warn("Invalid enchant '" + enchantName + "' in item " + itemId);
                }
            } catch (Exception ignored) {
                DebugLogger.warn("Error parsing enchant '" + enchantExpr + "' in item " + itemId);
            }
        }
    }

    private static void applyDataKeys(ItemBuilder builder, List<String> entries, String itemId) {
        for (String entry : entries) {
            try {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    builder.addKey(parts[0], parts[1]);
                }
            } catch (Exception ignored) {
                DebugLogger.warn("Error parsing data key '" + entry + "' in item " + itemId);
            }
        }
    }

    /**
     * Parses a colour string in one of three forms:
     * <ul>
     *   <li>{@code "r,g,b"} — decimal triplet (0-255 each)</li>
     *   <li>{@code "#RRGGBB"} — hex string</li>
     *   <li>{@code "&c"} — Bukkit colour code mapped to its named RGB triple</li>
     * </ul>
     */
    private static int[] parseColor(String raw) {
        String trimmed = raw.trim();
        try {
            if (trimmed.matches("\\d{1,3},\\d{1,3},\\d{1,3}")) {
                String[] parts = trimmed.split(",");
                return new int[]{Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim())};
            }
            if (trimmed.startsWith("#") && trimmed.length() == 7) {
                int hex = Integer.parseInt(trimmed.substring(1), 16);
                return new int[]{(hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF};
            }
            if (trimmed.startsWith("&") && trimmed.length() == 2) {
                NamedTextColor named = Text.getNamedTextColor(trimmed.charAt(1));
                if (named != null) {
                    return new int[]{named.red(), named.green(), named.blue()};
                }
            }
        } catch (Exception ignored) {
            DebugLogger.warn("Invalid RGB/color string: " + raw);
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Getters
    // ─────────────────────────────────────────────────────────────────────────────

    public String getId()                       { return this.id; }
    public String getMaterial()                 { return this.material; }
    public int getAmount()                      { return this.amount; }
    public String getName()                     { return this.name; }
    public String getHeadTexture()              { return this.headTexture; }
    public int[] getRgb()                       { return this.rgb; }
    public Map<Enchantment, Integer> getEnchants() { return this.enchants; }
    public Set<ItemFlag> getFlags()             { return this.flags; }
    public List<String> getLore()               { return this.lore; }
    public List<ItemKey> getInternalKeys()      { return this.internalKeys; }
    public int getCustomModelData()             { return this.customModelData; }

    @Override
    public String toString() {
        return "ItemBuilder(id=" + this.id
                + ", material=" + this.material
                + ", amount=" + this.amount
                + ", name=" + this.name
                + ", headTexture=" + this.headTexture
                + ", rgb=" + Arrays.toString(this.rgb)
                + ", enchants=" + this.enchants
                + ", flags=" + this.flags
                + ", lore=" + this.lore
                + ", internalKeys=" + this.internalKeys
                + ", customModelData=" + this.customModelData + ")";
    }
}
