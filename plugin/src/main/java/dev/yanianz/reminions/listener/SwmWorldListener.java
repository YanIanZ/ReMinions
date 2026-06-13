package dev.yanianz.reminions.listener;

import dev.iyanz.sourbycraft.swm.api.events.LoadSlimeWorldEvent;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.managers.PlayerManager;
import dev.yanianz.reminions.managers.SkinManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class SwmWorldListener implements Listener {

    private final ReMinions plugin;
    private final PlayerManager playerManager;
    private final SkinManager skinManager;

    public SwmWorldListener(ReMinions plugin, PlayerManager playerManager, SkinManager skinManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.skinManager = skinManager;
    }

    @EventHandler
    public void onSlimeWorldLoad(LoadSlimeWorldEvent event) {
        Config config = this.plugin.getConfig0();
        if (!config.getBoolean("settings.swm_integration", true)) return;
        String worldName = event.getSlimeWorld().getName();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            for (Minion minion : this.playerManager.getAllMinions()) {
                if (!worldName.equals(minion.getLoc().getWorldName())) continue;
                if (minion.getLoc().toLocation() == null) continue;
                if (minion.getSkinModel() != null) continue;
                MinionSkinConfig skin = this.skinManager.get(minion.getCurrentSkin());
                if (skin == null) continue;
                minion.spawn(skin);
            }
        });
    }
}
