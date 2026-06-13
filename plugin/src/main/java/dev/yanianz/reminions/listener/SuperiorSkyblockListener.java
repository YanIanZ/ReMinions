package dev.yanianz.reminions.listener;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.managers.PlayerManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public class SuperiorSkyblockListener implements Listener {

    private final PlayerManager playerManager;

    public SuperiorSkyblockListener(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @EventHandler
    public void onIslandDisband(IslandDisbandEvent event) {
        Island island = event.getIsland();
        List<Minion> allMinions = this.playerManager.getAllMinions();
        for (Minion minion : List.copyOf(allMinions)) {
            Location loc = minion.getLoc().toLocation();
            if (loc != null && island.isInside(loc)) {
                minion.despawn();
                this.playerManager.removeMinion(
                        this.playerManager.getById(minion.getOwner()),
                        minion
                );
            }
        }
    }
}
