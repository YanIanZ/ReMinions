package dev.yanianz.reminions.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.yanianz.reminions.core.minion.Minion;
import org.bukkit.Location;

/**
 * Chunk-bucketed index that answers "what minions live within R blocks of point P".
 * Replaces the O(entity_count) {@code World#getNearbyEntities} scan we used to do
 * on every minion placement / proximity probe.
 *
 * Index is keyed by {@code worldUid + (chunkX << 32 | chunkZ)} so worlds are isolated.
 * Each bucket holds the minion UUIDs that own an armorstand inside that chunk.
 * Queries fan out by reading buckets in the inclusive chunk window covering the
 * search radius — typically 1–3 chunks per axis.
 */
public final class MinionSpatialIndex {
   private static final int CHUNK_SHIFT = 4; // 16 blocks per chunk

   /** worldUid -> chunkKey -> set of minion UUIDs in that chunk */
   private final Map<UUID, Map<Long, Set<UUID>>> byWorld = new ConcurrentHashMap<>();
   /** quick reverse lookup of currently indexed bucket for a minion (for removal) */
   private final Map<UUID, Slot> minionSlot = new ConcurrentHashMap<>();

   public void add(Minion minion) {
      Location loc = minion.getLoc().toLocation();
      if (loc == null || loc.getWorld() == null) return;
      Slot slot = new Slot(loc.getWorld().getUID(), chunkKey(loc.getBlockX() >> CHUNK_SHIFT, loc.getBlockZ() >> CHUNK_SHIFT));
      Slot old = minionSlot.put(minion.getId(), slot);
      if (old != null && !old.equals(slot)) removeFromBucket(old, minion.getId());
      byWorld.computeIfAbsent(slot.worldUid, k -> new ConcurrentHashMap<>())
             .computeIfAbsent(slot.chunkKey, k -> ConcurrentHashMap.newKeySet())
             .add(minion.getId());
   }

   public void remove(UUID minionId) {
      Slot slot = minionSlot.remove(minionId);
      if (slot != null) removeFromBucket(slot, minionId);
   }

   public void clear() {
      byWorld.clear();
      minionSlot.clear();
   }

   /** Returns minion UUIDs whose chunk overlaps the radius window around {@code center}. */
   public List<UUID> queryNearby(Location center, double radius) {
      if (center == null || center.getWorld() == null) return Collections.emptyList();
      Map<Long, Set<UUID>> world = byWorld.get(center.getWorld().getUID());
      if (world == null || world.isEmpty()) return Collections.emptyList();

      int minChunkX = ((int) Math.floor(center.getX() - radius)) >> CHUNK_SHIFT;
      int maxChunkX = ((int) Math.floor(center.getX() + radius)) >> CHUNK_SHIFT;
      int minChunkZ = ((int) Math.floor(center.getZ() - radius)) >> CHUNK_SHIFT;
      int maxChunkZ = ((int) Math.floor(center.getZ() + radius)) >> CHUNK_SHIFT;

      List<UUID> out = new ArrayList<>(8);
      Set<UUID> seen = new HashSet<>();
      for (int cx = minChunkX; cx <= maxChunkX; cx++) {
         for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            Set<UUID> bucket = world.get(chunkKey(cx, cz));
            if (bucket == null) continue;
            for (UUID id : bucket) {
               if (seen.add(id)) out.add(id);
            }
         }
      }
      return out;
   }

   private void removeFromBucket(Slot slot, UUID minionId) {
      Map<Long, Set<UUID>> world = byWorld.get(slot.worldUid);
      if (world == null) return;
      Set<UUID> bucket = world.get(slot.chunkKey);
      if (bucket == null) return;
      bucket.remove(minionId);
      if (bucket.isEmpty()) world.remove(slot.chunkKey);
   }

   private static long chunkKey(int cx, int cz) {
      return (((long) cx) << 32) | (cz & 0xffffffffL);
   }

   private record Slot(UUID worldUid, long chunkKey) {}

   public static int affectedRadiusBlocks(double radius) {
      // Used by callers as upper bound — exposed for tests / docs.
      return (int) Math.ceil(radius) + 1;
   }
}
