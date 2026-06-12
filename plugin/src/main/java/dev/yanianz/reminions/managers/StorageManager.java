package dev.yanianz.reminions.managers;

import java.util.List;
import dev.yanianz.reminions.config.StorageConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.FileTreeHandler;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** Loads storage YAML configs that define block skin, item, and max capacity. */
public class StorageManager extends FileTreeHandler<StorageConfig> {

    private static final List<String> DEFAULT_STORAGE_FILES = List.of("basic_storage.yml");

    public StorageManager() {
        super(
                "storages",
                "storages",
                DEFAULT_STORAGE_FILES,
                (path, id) -> {},
                (path, id) -> DebugLogger.debug(String.format("Storage '%s' loaded successfully!", id)));
    }

    @Override
    public StorageConfig load(ConfigurationSection section, String storageId) {
        String displayName = section.getString("name", storageId);

        String blockSkinName = section.getString("block_skin", "CHEST");
        Material blockSkin = Material.matchMaterial(blockSkinName.toUpperCase());
        if (blockSkin == null) {
            blockSkin = Material.CHEST;
            DebugLogger.debug("Invalid block_skin in " + storageId + ", defaulting to CHEST.");
        }

        ConfigurationSection itemSection = section.getConfigurationSection("item");
        ItemBuilder itemBuilder = null;
        if (itemSection != null) {
            itemBuilder = ItemBuilder.buildToFile(null, itemSection).addKey("storage_item", storageId);
        }

        int maxStorage = section.getInt("max_storage", 1);
        List<Integer> pattern = section.getIntegerList("pattern");
        int rows = section.getInt("rows", 5);

        return new StorageConfig(storageId, displayName, blockSkin, itemBuilder, maxStorage, pattern, rows);
    }
}
