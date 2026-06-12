package dev.yanianz.reminions.nms;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.managers.MinionManager;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public interface NMSHandler {
    void sendGhostRecipe(PlayerRecipeBookClickEvent event, Config config, MinionManager minionManager,
                         Recipe recipe, ItemStack resultItem);

    void updateInventoryTitle(InventoryView inventoryView, String newTitle);
}
