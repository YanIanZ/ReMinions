package dev.yanianz.v1_20_3;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.nms.NMSHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Real NMS adapter for Paper 1.20.3 – 1.20.4. Predates the 1.21.2 SlotDisplay API and the 1.20.5
 * Mojang-mapped runtime, so the impl sends the ghost recipe as a recipe-id reference and
 * the shadowJar consumes this module's {@code reobfJar} output (Spigot-relocated names).
 */
public final class NMSHandlerImpl_1_20_3 implements NMSHandler {

    @Override
    public void sendGhostRecipe(PlayerRecipeBookClickEvent event, Config config, MinionManager minionManager,
                                Recipe recipe, ItemStack resultItem) {
        if (!(recipe instanceof ShapedRecipe shaped)) return;
        Player player = event.getPlayer();
        NamespacedKey key = shaped.getKey();
        try {
            NMSHandlerImpl_1_20_3_LegacyGhostRecipeSender.send((CraftPlayer) player, key);
        } catch (Throwable ignored) {
            // Older Spigot-mapped Paper builds occasionally vary the ctor — skip silently.
        }
    }

    @Override
    public void updateInventoryTitle(InventoryView inventoryView, String newTitle) {
        CraftInventoryView.sendInventoryTitleChange(inventoryView, newTitle);
    }
}
