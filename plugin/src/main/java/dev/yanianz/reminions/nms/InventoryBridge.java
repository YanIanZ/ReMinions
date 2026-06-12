package dev.yanianz.reminions.nms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

public interface InventoryBridge {
    InventoryHolder getTopHolder(Player player);
    void closeInventoryPlayers();
}
