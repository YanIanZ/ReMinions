package dev.yanianz.reminions.managers.importer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.stream.Stream;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.Text;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class MinionBatchImporter {
    private final Yaml loaderYaml;
    private final Yaml dumperYaml;
    private final File importFolder;
    private final File outputFolder;

    public MinionBatchImporter(File importFolder, File outputFolder) {
        this.importFolder = importFolder;
        this.outputFolder = outputFolder;
        LoaderOptions loaderOptions = new LoaderOptions();
        this.loaderYaml = new Yaml(new SafeConstructor(loaderOptions));
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setDefaultScalarStyle(ScalarStyle.PLAIN);
        this.dumperYaml = new Yaml(dumperOptions);
    }

    public void importAll() throws java.io.IOException {
        if (!this.importFolder.exists()) {
            throw new IllegalStateException("Import folder does not exist: " + this.importFolder.getAbsolutePath());
        }
        if (!this.outputFolder.exists() && !this.outputFolder.mkdirs()) {
            throw new IllegalStateException("Could not create output folder: " + this.outputFolder.getAbsolutePath());
        }
        DebugLogger.info("[Importer] Starting batch import from: " + this.importFolder.getAbsolutePath());

        try (Stream<Path> paths = Files.walk(this.importFolder.toPath())) {
            paths.filter(p -> {
                String name = p.toString().toLowerCase(Locale.ROOT);
                return Files.isRegularFile(p) && (name.endsWith(".yml") || name.endsWith(".yaml"));
            }).forEach(p -> {
                File file = p.toFile();
                try {
                    this.processFile(file);
                } catch (Exception e) {
                    DebugLogger.error("[Importer] Failed to process file " + file.getAbsolutePath() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
        DebugLogger.info("[Importer] Batch import finished.");
    }

    @SuppressWarnings("unchecked")
    private void processFile(File file) {
        DebugLogger.info("[Importer] Processing file: " + file.getAbsolutePath());

        Map<String, Object> raw;
        try (FileReader reader = new FileReader(file)) {
            Object loaded = this.loaderYaml.load(reader);
            if (!(loaded instanceof Map<?, ?>)) {
                DebugLogger.warn("[Importer] Skipping file (not a YAML map): " + file.getName());
                return;
            }
            raw = (Map<String, Object>) loaded;
        } catch (Exception e) {
            DebugLogger.error("[Importer] Error reading YAML: " + file.getName() + " - " + e.getMessage());
            throw new RuntimeException(e);
        }

        if (raw.isEmpty()) {
            DebugLogger.warn("[Importer] Empty or invalid YAML, skipping: " + file.getName());
            return;
        }

        boolean isOldFormat = raw.containsKey("entityKill-generated")
                || raw.containsKey("max-level")
                || raw.containsKey("time-between-action")
                || raw.containsKey("max-storage")
                || raw.containsKey("skin");
        if (!isOldFormat) {
            DebugLogger.info("[Importer] File already in new format, skipping: " + file.getName());
            return;
        }

        Map<String, Object> converted;
        try {
            converted = this.convertOldToNew(raw, file.getName().replace(".yml", ""));
        } catch (Exception e) {
            DebugLogger.error("[Importer] Conversion failed for " + file.getName() + ": " + e.getMessage());
            throw new RuntimeException(e);
        }

        Path relative = this.importFolder.toPath().relativize(file.toPath());
        File outFile = new File(this.outputFolder, relative.toString());
        File outDir = outFile.getParentFile();
        if (outDir != null && !outDir.exists() && !outDir.mkdirs()) {
            DebugLogger.warn("[Importer] Could not create directories for: " + outDir.getAbsolutePath());
        }

        try (FileWriter writer = new FileWriter(outFile)) {
            this.dumperYaml.dump(converted, writer);
            DebugLogger.info("[Importer] Converted: " + file.getName() + " -> " + outFile.getAbsolutePath());
        } catch (Exception e) {
            DebugLogger.error("[Importer] Failed to write converted file: " + outFile.getAbsolutePath() + " - " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertOldToNew(Map<String, Object> old, String minionId) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();

        String displayName = safeGetString(old, "name", "Unknown Minion");
        result.put("name", displayName);

        Map<String, Object> itemSection = (Map<String, Object>) old.get("item");
        LinkedHashMap<String, Object> itemOut = new LinkedHashMap<>();
        itemOut.put("name", safeGetString(itemSection, "name", "").replace("%level%", "%roman_level%"));
        itemOut.put("material", safeGetString(itemSection, "material", "PLAYER_HEAD"));
        List<String> lore = (List<String>) itemSection.get("lore");
        lore.replaceAll(line -> line
                .replace("%max_storage%", "%storage%")
                .replace("%collected_items%", "%collected%")
                .replace("%time_between_action%", "%production_speed%")
                .replace("%level%", "%roman_level%"));
        itemOut.put("lore", lore);
        result.put("item", itemOut);

        String rawType = safeGetString(old, "type", "UNKNOWN").toUpperCase(Locale.ROOT);
        String mappedType = mapType(rawType);
        result.put("type", mappedType);

        String baseSkin = safeGetString(old, "skin", "default_skin");

        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        properties.put("base_radius", safeGetInt(old, "base-radius", 2));

        Map<String, Object> layoutSection = (Map<String, Object>) old.get("layout");
        Map<String, Object> layoutItems = (Map<String, Object>) layoutSection.get("items");
        List<String> blockList = new ArrayList<>();
        for (Entry<String, Object> entry : layoutItems.entrySet()) {
            Map<String, Object> itemEntry = (Map<String, Object>) entry.getValue();
            String mat = (String) itemEntry.get("material");
            if (!mat.equalsIgnoreCase("BEDROCK")) blockList.add(mat.toUpperCase());
        }
        properties.put("block_check_around", String.join(",", blockList));

        LinkedHashMap<String, Object> animations = new LinkedHashMap<>();
        switch (mappedType) {
            case "MINER"     -> animations.put("block", safeGetString(old, "resource-generated", "STONE"));
            case "FARMER"    -> { animations.put("crop", safeGetString(old, "crop-generated", "WHEAT")); animations.put("crop_particle", "HAPPY_VILLAGER"); }
            case "KILLER"    -> { animations.put("entity", safeGetString(old, "entity-generated", "ZOMBIE")); animations.put("entity_particle", "VILLAGER_ANGRY"); }
            case "FISHERMAN" -> { animations.put("entity_catch", "SQUID"); animations.put("entity_particle_catch", "WATER_BUBBLE"); }
        }
        properties.put("animations", animations);
        result.put("properties", properties);

        LinkedHashMap<String, Object> products = new LinkedHashMap<>();
        if (old.get("products") instanceof Map<?, ?> prodMap) {
            for (Entry<?, ?> entry : prodMap.entrySet()) {
                if (!(entry.getValue() instanceof Map)) continue;
                Map<String, Object> prodData = (Map<String, Object>) entry.getValue();
                String prodKey = entry.getKey().toString();
                LinkedHashMap<String, Object> prodOut = new LinkedHashMap<>();
                String material = safeGetString(prodData, "material", safeGetString(prodData, "type", "UNKNOWN")).toUpperCase(Locale.ROOT);
                prodOut.put("type", safeGetString(prodData, "type", "vanilla"));
                prodOut.put("material", material);
                double chance = safeGetDouble(prodData, "drop-chance", safeGetDouble(prodData, "chance", 1.0));
                prodOut.put("chance", chance);
                Object amount = prodData.get("amount");
                prodOut.put("amount", Objects.requireNonNullElse(amount, 1));
                if (prodData.containsKey("price")) prodOut.put("price", prodData.get("price"));
                if (prodData.containsKey("filter-exp")) {
                    Map<String, Object> expData = (Map<String, Object>) prodData.get("filter-exp");
                    LinkedHashMap<String, Object> expOut = new LinkedHashMap<>();
                    expOut.put("skill", expData.get("skill-id"));
                    expOut.put("exp", expData.get("exp-gain"));
                    prodOut.put("source_exp", expOut);
                }
                products.put(prodKey, prodOut);
            }
        }
        result.put("products", products);

        int maxLevel = safeGetInt(old, "max-level", 6);
        int baseSpeed = safeGetInt(old, "time-between-action", safeGetInt(old, "time_between_action", 30));
        int baseStorage = safeGetInt(old, "max-storage", safeGetInt(old, "max_storage", 5));

        LinkedHashMap<Integer, Map<String, Object>> skinLevels = new LinkedHashMap<>();
        LinkedHashMap<String, Object> skinLevel1 = new LinkedHashMap<>();
        skinLevel1.put("path", baseSkin);
        skinLevels.put(1, skinLevel1);

        LinkedHashMap<Integer, Map<String, Object>> upgrades = new LinkedHashMap<>();
        LinkedHashMap<String, Object> upgrade1 = new LinkedHashMap<>();
        upgrade1.put("production_speed", baseSpeed);
        upgrade1.put("max_storage", baseStorage);
        upgrades.put(1, upgrade1);

        Map<Integer, Map<String, Object>> convertedUpgrades = this.convertUpgrades(old, maxLevel, baseSpeed, baseStorage, skinLevels, minionId);
        upgrades.putAll(convertedUpgrades);
        result.put("upgrades", upgrades);
        result.put("skin_levels", skinLevels);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Map<String, Object>> convertUpgrades(Map<String, Object> old, int maxLevel,
            int baseSpeed, int baseStorage, Map<Integer, Map<String, Object>> skinLevels, String minionId) {
        LinkedHashMap<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        if (!old.containsKey("upgrades")) return result;

        Map<Integer, Map<String, Object>> upgradesRaw = (Map<Integer, Map<String, Object>>) old.get("upgrades");
        for (Entry<Integer, Map<String, Object>> entry : upgradesRaw.entrySet()) {
            int upgradeIndex = entry.getKey();
            Map<String, Object> upgradeData = entry.getValue();

            LinkedHashMap<String, Object> upgradeOut = new LinkedHashMap<>();
            double speed = safeGetDouble(upgradeData, "new-between-action", baseSpeed);
            upgradeOut.put("production_speed", Math.max(5.0, speed));
            int storage = safeGetInt(upgradeData, "new-storage", baseStorage);
            upgradeOut.put("max_storage", storage);

            LinkedHashMap<String, Object> requirement = new LinkedHashMap<>();
            Map<String, Object> recipeSection = (Map<String, Object>) upgradeData.get("recipe");
            if (recipeSection != null) {
                Map<String, Object> recipeItems = (Map<String, Object>) recipeSection.get("items");
                LinkedHashMap<String, Object> itemsOut = new LinkedHashMap<>();
                char airSymbol = '-';

                for (Entry<String, Object> itemEntry : recipeItems.entrySet()) {
                    String symbol = itemEntry.getKey();
                    Map<String, Object> itemData = (Map<String, Object>) itemEntry.getValue();
                    LinkedHashMap<String, Object> itemOut = new LinkedHashMap<>();
                    String itemType = (String) itemData.getOrDefault("type", "vanilla");
                    itemOut.put("amount", safeGetInt(itemData, "amount", 1));
                    LinkedHashMap<String, Object> product = new LinkedHashMap<>();

                    switch (itemType) {
                        case "vanilla" -> {
                            Material mat = Material.valueOf(String.valueOf(itemData.get("material")));
                            if (mat == Material.AIR) { airSymbol = symbol.charAt(0); continue; }
                            String matName = mat.name();
                            product.put("type", "vanilla");
                            product.put("material", matName);
                            if (itemData.containsKey("name")) product.put("name", itemData.get("name"));
                            if (itemData.containsKey("lore")) product.put("lore", itemData.get("lore"));
                            itemOut.put("display", "&f" + prettifyProductKey(matName));
                        }
                        case "mmoitem" -> {
                            String category = (String) itemData.get("category");
                            String itemId = (String) itemData.get("id");
                            if (ReMinions.getPlugin().isMMOItemsInstalled()) {
                                MMOItem mmoItem = MMOItems.plugin.getMMOItem(Type.get(category), itemId);
                                if (mmoItem != null) {
                                    ItemStack built = mmoItem.newBuilder().build();
                                    itemOut.put("display", Text.toLegacyString(built.displayName()));
                                }
                            }
                            product.put("type", "mmoitem");
                            product.put("category", category);
                            product.put("id", itemId);
                        }
                    }

                    itemOut.put("product", product);
                    itemsOut.put(symbol, itemOut);
                }

                requirement.put("items", itemsOut);
                List<String> shape = (List<String>) recipeSection.get("shape");
                requirement.put("shape", airSymbol != '-' ? getStrings(airSymbol, shape) : shape);
            }

            upgradeOut.put("requirement", requirement);

            LinkedHashMap<String, Object> skinEntry = new LinkedHashMap<>();
            skinEntry.put("path", upgradeData.get("skin-level"));
            skinLevels.put(upgradeIndex + 1, skinEntry);
            result.put(upgradeIndex + 1, upgradeOut);
        }
        return result;
    }

    @NotNull
    private static List<String> getStrings(char airSymbol, List<String> shape) {
        List<String> result = new ArrayList<>();
        if (airSymbol == '-') return result;
        int midRow = shape.size() / 2;
        int midCol = shape.getFirst().length() / 2;
        for (int row = 0; row < shape.size(); row++) {
            String line = shape.get(row);
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (row == midRow && col == midCol) sb.append('?');
                else if (c == airSymbol)            sb.append(' ');
                else                                sb.append(c);
            }
            result.add(sb.toString());
        }
        return result;
    }

    private static String prettifyProductKey(String material) {
        if (material == null) return "Item";
        String[] words = material.replace('_', ' ').toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static String mapType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "COMBAT"   -> "KILLER";
            case "FORAGING" -> "LUMBERJACK";
            case "FISHING"  -> "FISHERMAN";
            case "FARMING"  -> "FARMER";
            case "MINING"   -> "MINER";
            default         -> type.toUpperCase(Locale.ROOT);
        };
    }

    private static int safeGetInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return def; }
    }

    private static double safeGetDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val == null) return def;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return def; }
    }

    private static String safeGetString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val == null ? def : val.toString();
    }
}
