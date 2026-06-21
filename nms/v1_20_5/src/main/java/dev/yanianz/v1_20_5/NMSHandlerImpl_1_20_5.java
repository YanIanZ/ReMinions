package dev.yanianz.v1_20_5;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.nms.NMSHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Real NMS adapter for Paper 1.20.5 - 1.20.6. Predates the 1.21.2 SlotDisplay API, so the ghost recipe
 * is sent as a recipe-id reference; the client renders whatever recipe is registered locally.
 * updateInventoryTitle uses the real CraftInventoryView helper.
 */
public final class NMSHandlerImpl_1_20_5 implements NMSHandler {

    @Override
    public void sendGhostRecipe(PlayerRecipeBookClickEvent event, Config config, MinionManager minionManager,
                                Recipe recipe, ItemStack resultItem) {
        if (!(recipe instanceof ShapedRecipe shaped)) return;
        NamespacedKey key = shaped.getKey();
        try {
            NMSHandlerImpl_1_20_5_LegacyGhostRecipeSender.send((CraftPlayer) event.getPlayer(), key);
        } catch (Throwable ignored) {
            // NMS packet ctor may have shifted between minor versions; fall back silently —
            // server still accepts the craft, player just doesn't see the auto-fill preview.
        }
    }

    @Override
    public void updateInventoryTitle(InventoryView inventoryView, String newTitle) {
        CraftInventoryView.sendInventoryTitleChange(inventoryView, newTitle);
    }
}
