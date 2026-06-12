package dev.yanianz.v1_20_1;

import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.nms.InventoryBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class InventoryBridge_v1_20_1 implements InventoryBridge {
   @Override
   public InventoryHolder getTopHolder(Player player) {
      return player.getOpenInventory().getTopInventory().getHolder();
   }

   @Override
   public void closeInventoryPlayers() {
      Bukkit.getOnlinePlayers().stream().filter(player -> {
         Inventory var1 = player.getOpenInventory().getTopInventory();
         return var1 != null && var1.getHolder() instanceof MenuHolder;
      }).forEach(HumanEntity::closeInventory);
   }
}
