package dev.yanianz.reminions.managers.importer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.stream.Stream;
import dev.yanianz.reminions.utils.DebugLogger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class ModifierBatchImporter {
    private final Yaml loaderYaml;
    private final Yaml dumperYaml;
    private final File importFolder;
    private final File outputFolder;

    public ModifierBatchImporter(File importFolder, File outputFolder) {
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
    private void processFile(File file) throws java.io.IOException {
        DebugLogger.info("[Importer] Processing file: " + file.getAbsolutePath());

        Map<String, Object> raw;
        try (FileReader reader = new FileReader(file)) {
            Object loaded = this.loaderYaml.load(reader);
            if (!(loaded instanceof Map<?, ?>)) {
                DebugLogger.warn("[Importer] Skipping file (not a YAML map): " + file.getName());
                return;
            }
            raw = (Map<String, Object>) loaded;
        }

        if (raw.isEmpty()) {
            DebugLogger.warn("[Importer] Empty or invalid YAML, skipping: " + file.getName());
            return;
        }

        Map<String, Object> converted = this.convertOldToNew(raw);
        Path relative = this.importFolder.toPath().relativize(file.toPath());
        File outFile = new File(this.outputFolder, relative.toString());
        File outDir = outFile.getParentFile();
        if (outDir != null && !outDir.exists()) outDir.mkdirs();

        try (FileWriter writer = new FileWriter(outFile)) {
            this.dumperYaml.dump(converted, writer);
            DebugLogger.info("[Importer] Converted: " + file.getName() + " -> " + outFile.getAbsolutePath());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertOldToNew(Map<String, Object> old) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        String type = this.safeGetString(old, "type", "UNKNOWN").toUpperCase(Locale.ROOT);

        switch (type) {
            case "SPEED" -> {
                result.put("category", "FUEL");
                result.put("type", "SPEED");
                result.put("duration", this.toMinutes(old.get("duration")));
                result.put("multiplier", this.safeGetDouble(old, "value", 0.0) / 100.0);
                result.put("unbreakable", false);
            }
            case "AUTO_SELL" -> {
                result.put("category", "SELL");
                result.put("type", "AUTO_SELL");
                result.put("duration", -1);
                result.put("unbreakable", this.safeGetBool(old, "unbreakable", true));
            }
            case "UPGRADE_ITEMS" -> {
                result.put("category", "UPGRADE");
                result.put("type", "ITEM_UPGRADES");
                result.put("duration", -1);
                result.put("multiplier", 1);
                result.put("unbreakable", this.safeGetBool(old, "unbreakable", true));

                LinkedHashMap<String, Object> upgradeProducts = new LinkedHashMap<>();
                if (old.get("products") instanceof Map<?, ?> prodMap) {
                    for (Entry<?, ?> entry : prodMap.entrySet()) {
                        if (!(entry.getValue() instanceof Map)) continue;
                        Map<String, Object> prodData = (Map<String, Object>) entry.getValue();
                        String prodKey = entry.getKey().toString();
                        LinkedHashMap<String, Object> prodOut = new LinkedHashMap<>();
                        String prodType = this.safeGetString(prodData, "type", "vanilla").toLowerCase();
                        prodOut.put("type", prodType);
                        switch (prodType) {
                            case "vanilla" -> {
                                String mat = this.safeGetString(prodData, "material",
                                        this.safeGetString(prodData, "type", "STONE")).toUpperCase();
                                prodOut.put("material", mat);
                            }
                            case "mmoitem" -> {
                                prodOut.put("id", this.safeGetString(prodData, "id", ""));
                                prodOut.put("category", this.safeGetString(prodData, "category", ""));
                            }
                        }
                        double chance = this.safeGetDouble(prodData, "drop-chance",
                                this.safeGetDouble(prodData, "chance", 1.0));
                        prodOut.put("chance", chance);
                        prodOut.put("required_product", this.safeGetString(prodData, "requiered-to", ""));
                        prodOut.put("required_product_amount", this.safeGetInt(prodData, "required-upgrade", 1));
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
                        upgradeProducts.put(prodKey, prodOut);
                    }
                }
                result.put("upgrade_products", upgradeProducts);
            }
        }

        Map<String, Object> itemSection = (Map<String, Object>) old.get("item");
        if (itemSection != null) result.put("item", itemSection);
        return result;
    }

    private int toMinutes(Object val) {
        return val instanceof Number n ? n.intValue() * 60 : -1;
    }

    private String safeGetString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }

    private int safeGetInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return def; }
    }

    private double safeGetDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { return def; }
    }

    private boolean safeGetBool(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        return val != null ? Boolean.parseBoolean(val.toString()) : def;
    }
}
