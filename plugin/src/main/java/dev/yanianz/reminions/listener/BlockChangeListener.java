package dev.yanianz.reminions.listener;

import java.util.UUID;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.core.minion.Minion;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Invalidates the per-minion isValid() cache when a block changes near any
 * indexed minion. Spatial index gives us a tight chunk-bucket scan instead of
 * iterating every loaded minion on every block edit.
 */
public final class BlockChangeListener implements Listener {

   private static final int MAX_AFFECT_RADIUS = 32;

   private final ReMinions plugin;

   public BlockChangeListener(ReMinions plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent e) {
      invalidateAround(e.getBlock());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent e) {
      invalidateAround(e.getBlock());
   }

   private void invalidateAround(Block block) {
      if (block == null) return;
      Location loc = block.getLocation();
      for (UUID id : this.plugin.getPlayerManager().spatialIndex().queryNearby(loc, MAX_AFFECT_RADIUS)) {
         Minion m = this.plugin.getPlayerManager().getMinionById(id);
         if (m == null) continue;
         // Only invalidate if the changed block is actually inside this minion's
         // radius footprint. Cheap math; avoids re-validating unrelated minions
         // that just happen to share the same chunk.
         if (m.getLoc() == null) continue;
         double dx = Math.abs(loc.getX() - m.getLoc().getX());
         double dz = Math.abs(loc.getZ() - m.getLoc().getZ());
         double r = m.getBaseRadius() + 2; // +2 slack for modifier radius bumps
         if (dx <= r && dz <= r) {
            m.invalidateValidCache();
         }
      }
   }
}
