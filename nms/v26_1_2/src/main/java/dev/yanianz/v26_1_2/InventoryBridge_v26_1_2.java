package dev.yanianz.v26_1_2;

import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.nms.InventoryBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class InventoryBridge_v26_1_2 implements InventoryBridge {
    @Override
    public InventoryHolder getTopHolder(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder();
    }

    @Override
    public void closeInventoryPlayers() {
        Bukkit.getOnlinePlayers().stream().filter(p -> {
            Inventory top = p.getOpenInventory().getTopInventory();
            return top != null && top.getHolder() instanceof MenuHolder;
        }).forEach(HumanEntity::closeInventory);
    }
}
