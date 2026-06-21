package dev.yanianz.v1_20;

import dev.yanianz.reminions.nms.ApiBackedNmsAdapter;
import dev.yanianz.reminions.nms.InventoryBridge;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/** Inventory bridge for 1.20.0 – 1.20.1. Uses Bukkit-API getTopInventory / closeInventory only. */
public final class InventoryBridge_v1_20 implements InventoryBridge {
    @Override
    public InventoryHolder getTopHolder(Player player) {
        return ApiBackedNmsAdapter.INVENTORY_BRIDGE.getTopHolder(player);
    }

    @Override
    public void closeInventoryPlayers() {
        ApiBackedNmsAdapter.INVENTORY_BRIDGE.closeInventoryPlayers();
    }
}
