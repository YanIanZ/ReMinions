package dev.yanianz.reminions.managers.importer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import dev.yanianz.reminions.utils.DebugLogger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class SkinBatchImporter {
    private final Yaml loaderYaml;
    private final Yaml dumperYaml;
    private final File importFolder;
    private final File outputFolder;

    public SkinBatchImporter(File importFolder, File outputFolder) {
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
        } catch (Exception e) {
            DebugLogger.error("[Importer] Error reading YAML: " + file.getName() + " - " + e.getMessage());
            throw new RuntimeException(e);
        }

        if (raw.isEmpty()) {
            DebugLogger.warn("[Importer] Empty or invalid YAML, skipping: " + file.getName());
            return;
        }
        if (!raw.containsKey("item")) {
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
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertOldToNew(Map<String, Object> old, String skinId) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        LinkedHashMap<String, Object> slots = new LinkedHashMap<>();

        Map<String, Object> headSection = this.getSection(old, 0);
        if (headSection != null) {
            LinkedHashMap<String, Object> headOut = new LinkedHashMap<>();
            headOut.put("material", safeGetString(headSection, "material", "PLAYER_HEAD"));
            headOut.put("head_texture", safeGetString(headSection, "texture", null));
            slots.put("HEAD", headOut);
        }

        Map<String, Object> chestSection = this.getSection(old, 1);
        if (chestSection != null) {
            LinkedHashMap<String, Object> chestOut = new LinkedHashMap<>();
            chestOut.put("material", safeGetString(chestSection, "material", "LEATHER_CHESTPLATE"));
            String color = safeGetString(chestSection, "color", null);
            if (color != null) chestOut.put("rgb", color);
            slots.put("CHEST", chestOut);
        }

        Map<String, Object> legsSection = this.getSection(old, 2);
        if (legsSection != null) {
            LinkedHashMap<String, Object> legsOut = new LinkedHashMap<>();
            legsOut.put("material", safeGetString(legsSection, "material", "LEATHER_LEGGINGS"));
            String color = safeGetString(legsSection, "color", null);
            if (color != null) legsOut.put("rgb", color);
            slots.put("LEGS", legsOut);
        }

        Map<String, Object> feetSection = this.getSection(old, 3);
        if (feetSection != null) {
            LinkedHashMap<String, Object> feetOut = new LinkedHashMap<>();
            feetOut.put("material", safeGetString(feetSection, "material", "LEATHER_BOOTS"));
            String color = safeGetString(feetSection, "color", null);
            if (color != null) feetOut.put("rgb", color);
            slots.put("FEET", feetOut);
        }

        Map<String, Object> handSection = this.getSection(old, 4);
        if (handSection != null) {
            LinkedHashMap<String, Object> handOut = new LinkedHashMap<>();
            handOut.put("material", safeGetString(handSection, "material", "STONE_PICKAXE"));
            slots.put("HAND", handOut);
        }

        if (!slots.isEmpty()) result.put("slots", slots);

        if (old.containsKey("holograms")) {
            Map<String, Object> holograms = (Map<String, Object>) old.get("holograms");
            LinkedHashMap<String, Object> holoOut = new LinkedHashMap<>();
            if (holograms.containsKey("block-place"))        holoOut.put("WORKING_PLACE",     (List<String>) holograms.get("block-place"));
            if (holograms.containsKey("block-idle"))         holoOut.put("WORKING_BREAK",      (List<String>) holograms.get("block-idle"));
            if (holograms.containsKey("position-invalid"))   holoOut.put("POSITION_INVALID",   (List<String>) holograms.get("position-invalid"));
            if (holograms.containsKey("full"))               holoOut.put("FULLY",              (List<String>) holograms.get("full"));
            result.put("holograms", holoOut);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSection(Map<String, Object> map, Object key) {
        Object val = map.get(key);
        if (val == null) val = map.get(String.valueOf(key));
        return (Map<String, Object>) val;
    }

    private static String safeGetString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val == null ? def : val.toString();
    }
}
