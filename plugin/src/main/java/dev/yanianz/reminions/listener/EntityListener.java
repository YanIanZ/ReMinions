package dev.yanianz.reminions.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Suppresses vanilla loot drops for mobs that were spawned by a killer minion
 * (tagged with {@code Keys.ENTITY_MINION} in their PDC). Prevents money / XP farms
 * via the minion-spawned entity loop.
 */
public class EntityListener implements Listener {

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        boolean spawnedByMinion = entity.getPersistentDataContainer()
                .has(dev.yanianz.reminions.Keys.ENTITY_MINION, PersistentDataType.STRING);
        if (spawnedByMinion) {
            event.getDrops().clear();
        }
    }
}
