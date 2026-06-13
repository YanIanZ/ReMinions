package dev.yanianz.reminions.listener;

import dev.iyanz.sourbycraft.swm.api.events.LoadSlimeWorldEvent;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.managers.PlayerManager;
import dev.yanianz.reminions.managers.SkinManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class SwmWorldListener implements Listener {

    private final PlayerManager playerManager;
    private final SkinManager skinManager;

    public SwmWorldListener(PlayerManager playerManager, SkinManager skinManager) {
        this.playerManager = playerManager;
        this.skinManager = skinManager;
    }

    @EventHandler
    public void onSlimeWorldLoad(LoadSlimeWorldEvent event) {
        String worldName = event.getSlimeWorld().getName();
        for (Minion minion : this.playerManager.getAllMinions()) {
            if (!worldName.equals(minion.getLoc().getWorldName())) continue;
            if (minion.getLoc().toLocation() == null) continue;
            if (minion.getSkinModel() != null) continue;
            MinionSkinConfig skin = this.skinManager.get(minion.getCurrentSkin());
            if (skin == null) continue;
            minion.spawn(skin);
        }
    }
}
