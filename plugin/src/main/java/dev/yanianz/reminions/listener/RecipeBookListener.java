package dev.yanianz.reminions.listener;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.yanianz.reminions.Keys;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Hooks the Paper {@link PlayerRecipeBookClickEvent} so that ghost-recipe previews for our
 * minion recipes show the actual required minion head instead of the generic placeholder.
 * Cancels the vanilla auto-craft flow when the player clicks a single recipe (we render
 * the preview via NMS) but leaves shift-click (make-all) as-is.
 */
public class RecipeBookListener implements Listener {

    private final MinionManager minionManager;
    private final Config config;

    public RecipeBookListener(MinionManager minionManager, Config config) {
        this.minionManager = minionManager;
        this.config = config;
    }

    @EventHandler
    public void onClickRecipeBook(PlayerRecipeBookClickEvent event) {
        if (!Keys.NAMESPACE.equals(event.getRecipe().getNamespace())) {
            return;
        }
        Recipe recipe = Bukkit.getRecipe(event.getRecipe());
        if (!(recipe instanceof ShapedRecipe shaped)) {
            return;
        }
        if (event.isMakeAll()) {
            return;
        }
        event.setCancelled(true);
        NMSHandlerProvider.getHandler().sendGhostRecipe(event, this.config, this.minionManager, recipe, shaped.getResult());
    }
}
