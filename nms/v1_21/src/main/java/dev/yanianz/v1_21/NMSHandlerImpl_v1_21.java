package dev.yanianz.v1_21;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.nms.ApiBackedNmsAdapter;
import dev.yanianz.reminions.nms.NMSHandler;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/** Adapter group for Paper builds in the 1.21.0 – 1.21.1 range. Delegates to the shared API-only impl. */
public final class NMSHandlerImpl_v1_21 implements NMSHandler {

    @Override
    public void sendGhostRecipe(PlayerRecipeBookClickEvent event, Config config, MinionManager minionManager,
                                Recipe recipe, ItemStack resultItem) {
        ApiBackedNmsAdapter.NMS_HANDLER.sendGhostRecipe(event, config, minionManager, recipe, resultItem);
    }

    @Override
    public void updateInventoryTitle(InventoryView inventoryView, String newTitle) {
        ApiBackedNmsAdapter.NMS_HANDLER.updateInventoryTitle(inventoryView, newTitle);
    }
}
