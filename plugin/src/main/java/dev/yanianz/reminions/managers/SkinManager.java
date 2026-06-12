package dev.yanianz.reminions.managers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.minion.MinionStatus;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.FileTreeHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;

/** Loads skin YAML configs that define armorstand equipment and hologram text per status. */
public class SkinManager extends FileTreeHandler<MinionSkinConfig> {

    private static final List<String> DEFAULT_SKIN_FILES = List.of(
            "cobblestone/cobblestone_level_1.yml",
            "cobblestone/cobblestone_level_2.yml",
            "cobblestone/cobblestone_level_3.yml",
            "cobblestone/cobblestone_level_4.yml",
            "cobblestone/cobblestone_level_5.yml",
            "cobblestone/cobblestone_level_6.yml",
            "cobblestone/supreme_cobblestone.yml",
            "wheat/wheat_level_1.yml",
            "wheat/wheat_level_2.yml",
            "wheat/wheat_level_3.yml",
            "wheat/wheat_level_4.yml",
            "wheat/wheat_level_5.yml",
            "wheat/wheat_level_6.yml",
            "jungle/jungle_level_1.yml",
            "jungle/jungle_level_2.yml",
            "jungle/jungle_level_3.yml",
            "jungle/jungle_level_4.yml",
            "jungle/jungle_level_5.yml",
            "jungle/jungle_level_6.yml",
            "zombie/zombie_level_1.yml",
            "zombie/zombie_level_2.yml",
            "zombie/zombie_level_3.yml",
            "zombie/zombie_level_4.yml",
            "zombie/zombie_level_5.yml",
            "zombie/zombie_level_6.yml",
            "zombie/supreme_zombie.yml",
            "squid/squid_level_1.yml",
            "squid/squid_level_2.yml",
            "squid/squid_level_3.yml",
            "squid/squid_level_4.yml",
            "squid/squid_level_5.yml",
            "squid/squid_level_6.yml");

    public SkinManager() {
        super(
                "skins",
                "skins",
                DEFAULT_SKIN_FILES,
                (path, id) -> {},
                (path, id) -> DebugLogger.debug(String.format("Skin '%s' loaded successfully!", id)));
    }

    @Override
    public MinionSkinConfig load(ConfigurationSection section, String skinId) {
        String category = section.getString("category");

        Map<MinionStatus, String[]> holograms = new HashMap<>();
        ConfigurationSection hologramSection = section.getConfigurationSection("holograms");
        if (hologramSection != null) {
            for (String statusKey : hologramSection.getKeys(false)) {
                MinionStatus status = MinionStatus.valueOf(statusKey.toUpperCase());
                List<String> lines = hologramSection.getStringList(statusKey);
                holograms.put(status, lines.toArray(new String[0]));
            }
        }

        MinionSkinConfig skinConfig = new MinionSkinConfig(category, holograms);

        ConfigurationSection slotsSection = section.getConfigurationSection("slots");
        if (slotsSection != null) {
            for (String slotKey : slotsSection.getKeys(false)) {
                ConfigurationSection slotSection = slotsSection.getConfigurationSection(slotKey);
                if (slotSection == null) continue;
                ItemBuilder slotItem = ItemBuilder.buildToFile(null, slotSection).addKey("skin_item", skinId);
                try {
                    EquipmentSlot slot = EquipmentSlot.valueOf(slotKey.toUpperCase());
                    skinConfig.addSlot(slot, slotItem);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return skinConfig;
    }
}
