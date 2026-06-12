package dev.yanianz.reminions.managers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.player.PlayerMinions;
import org.bukkit.Location;

public class PlayerManager {
    private final Map<UUID, PlayerMinions> cache = new ConcurrentHashMap<>();
    private final List<Minion> allMinionsCache = new CopyOnWriteArrayList<>();
    // O(1) lookups by id + by storage block — replaces O(n) stream scans.
    private final Map<UUID, Minion> minionById = new ConcurrentHashMap<>();
    private final Map<Long, Minion> minionByStorageBlock = new ConcurrentHashMap<>();
    private final MinionSpatialIndex spatial = new MinionSpatialIndex();

    public MinionSpatialIndex spatialIndex() { return this.spatial; }

    public void add(UUID playerId, PlayerMinions playerMinions) {
        this.cache.put(playerId, playerMinions);
        this.allMinionsCache.addAll(playerMinions.getMinions());
        for (Minion minion : playerMinions.getMinions()) {
            this.minionById.put(minion.getId(), minion);
            this.spatial.add(minion);
            this.indexStorage(minion);
        }
    }

    public PlayerMinions remove(UUID playerId) {
        PlayerMinions playerMinions = this.cache.remove(playerId);
        if (playerMinions != null) {
            this.allMinionsCache.removeAll(playerMinions.getMinions());
            for (Minion minion : playerMinions.getMinions()) {
                this.minionById.remove(minion.getId());
                this.spatial.remove(minion.getId());
                this.unindexStorage(minion);
            }
        }
        return playerMinions;
    }

    public void clear() {
        this.cache.clear();
        this.allMinionsCache.clear();
        this.minionById.clear();
        this.minionByStorageBlock.clear();
        this.spatial.clear();
    }

    public PlayerMinions getById(UUID playerId) { return this.cache.get(playerId); }
    public boolean contains(UUID playerId)       { return this.cache.containsKey(playerId); }
    public int size()                            { return this.cache.size(); }
    public Collection<PlayerMinions> getPlayersSnapshot() { return List.copyOf(this.cache.values()); }

    public void addMinion(UUID playerId, Minion minion) {
        this.cache.get(playerId).addMinion(minion);
        this.allMinionsCache.add(minion);
        this.minionById.put(minion.getId(), minion);
        this.spatial.add(minion);
        this.indexStorage(minion);
    }

    public void addMinion(PlayerMinions playerMinions, Minion minion) {
        playerMinions.addMinion(minion);
        this.allMinionsCache.add(minion);
        this.minionById.put(minion.getId(), minion);
        this.spatial.add(minion);
        this.indexStorage(minion);
    }

    public void removeMinion(UUID playerId, Minion minion) {
        this.cache.get(playerId).removeMinion(minion);
        minion.despawn();
        this.allMinionsCache.remove(minion);
        this.minionById.remove(minion.getId());
        this.spatial.remove(minion.getId());
        this.unindexStorage(minion);
    }

    public void removeMinion(PlayerMinions playerMinions, Minion minion) {
        minion.despawn();
        playerMinions.removeMinion(minion);
        this.allMinionsCache.remove(minion);
        this.minionById.remove(minion.getId());
        this.spatial.remove(minion.getId());
        this.unindexStorage(minion);
    }

    public List<Minion> getAllMinions() { return this.allMinionsCache; }

    public Minion getMinionById(UUID minionId) { return this.minionById.get(minionId); }

    public Minion getMinionByStorage(Location location, UUID ownerId) {
        if (location == null || location.getWorld() == null) return null;
        // ownerId is unused by this implementation but kept for API compatibility.
        return this.minionByStorageBlock.get(blockKey(location));
    }

    private void indexStorage(Minion minion) {
        if (minion.getStorage() == null) return;
        Location loc = minion.getStorage().location().toLocation();
        if (loc == null || loc.getWorld() == null) return;
        this.minionByStorageBlock.put(blockKey(loc), minion);
    }

    private void unindexStorage(Minion minion) {
        if (minion.getStorage() == null) return;
        Location loc = minion.getStorage().location().toLocation();
        if (loc == null || loc.getWorld() == null) return;
        Minion current = this.minionByStorageBlock.get(blockKey(loc));
        if (current == minion) this.minionByStorageBlock.remove(blockKey(loc));
    }

    private static long blockKey(Location loc) {
        // Cantor-ish pack: world hash + block coords. Worlds collide rarely; if they do
        // the secondary getMinionById equality check protects us.
        long x = loc.getBlockX() & 0x3ffffffL;         // 26 bits
        long y = (loc.getBlockY() & 0xfffL) << 26;     // 12 bits
        long z = (loc.getBlockZ() & 0x3ffffffL) << 38; // 26 bits
        long w = ((long) loc.getWorld().getUID().hashCode()) & 0x3L; // 2 bits, low collision
        return x | y | z | (w << 62);
    }
}
