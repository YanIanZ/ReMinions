package dev.yanianz.reminions.config;

import java.util.List;
import dev.yanianz.reminions.core.item.ItemBuilder;
import org.bukkit.Material;

public record StorageConfig(String id, String name, Material blockSkin, ItemBuilder item, int maxStorage, List<Integer> pattern, int rows) {
}
