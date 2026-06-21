package dev.yanianz.reminions.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.menu.impl.LayoutMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Generic dispatcher for any {@link MenuHolder} inventory.
 *
 * <ul>
 *   <li>Throttles clicks per-player to 4 Hz to prevent click-spam exploits.</li>
 *   <li>Periodically prunes the throttle map so it doesn't grow unbounded.</li>
 *   <li>Resolves the clicked item's {@code item_id} PDC key and forwards to {@code MenuHolder#onClick}.</li>
 *   <li>Cancels any pending animation task when a layout menu closes.</li>
 * </ul>
 */
public class MenuListener implements Listener {

    /** Minimum gap between two clicks of the same player (250 ms = 4 Hz). */
    private static final long CLICK_COOLDOWN_NS = TimeUnit.MILLISECONDS.toNanos(250);

    /** How often the throttle map is pruned of stale entries (30 s). */
    private static final long CLEANUP_INTERVAL_NS = TimeUnit.SECONDS.toNanos(30);

    /** UUID -> last click nanoTime. */
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private long lastCleanup = System.nanoTime();

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        long now = System.nanoTime();
        long previousClick = this.lastClickTime.getOrDefault(playerId, 0L);
        if (now - previousClick < CLICK_COOLDOWN_NS) {
            event.setCancelled(true);
            return;
        }

        this.lastClickTime.put(playerId, now);
        this.maybePruneThrottleMap(now);

        ItemKey itemKey = ItemBuilder.getPersistentKey(event.getCurrentItem(), "item_id");
        ItemBuilder item = itemKey != null ? holder.getItemById(itemKey.value()) : null;

        try {
            holder.onClick(player, item, event, event.getSlot());
        } catch (Exception ex) {
            ex.printStackTrace();
            event.setCancelled(true);
        }
    }

    private void maybePruneThrottleMap(long now) {
        if (now - this.lastCleanup <= CLEANUP_INTERVAL_NS) {
            return;
        }
        this.lastCleanup = now;
        long staleThreshold = now - CLEANUP_INTERVAL_NS;
        this.lastClickTime.entrySet().removeIf(e -> e.getValue() < staleThreshold);
    }

    /**
     * Blocks drags that touch the top inventory of any {@link MenuHolder}. The storage menu
     * (and other custom menus) must remain read-only from the player side — only the plugin
     * mutates these slots. Drags whose raw slots all fall in the player's own inventory pass
     * through unchanged.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LayoutMenu layoutMenu)) return;
        BukkitTask animationTask = layoutMenu.getAnimationTask();
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }
    }
}
