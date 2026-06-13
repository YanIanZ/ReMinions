package dev.yanianz.reminions.core.player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionModifierData;
import dev.yanianz.reminions.core.modifier.ModifierType;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public class PlayerMinions {
    private final UUID owner;
    private final String ownerName;
    private final Queue<Minion> minions = new ConcurrentLinkedQueue<>();
    private UUID viewMinion;
    private int maxMinions = 5;
    private final Map<String, Integer> minionUnlockeds = new HashMap<>();

    public PlayerMinions(@NotNull UUID owner, @NotNull String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public void addMinion(Minion minion) {
        if (minion != null) this.minions.add(minion);
    }

    public void addMinions(Collection<Minion> collection) {
        if (collection == null || collection.isEmpty()) return;
        for (Minion m : collection) {
            if (m != null) this.minions.add(m);
        }
    }

    public boolean removeMinionById(UUID id) {
        for (Minion minion : this.minions) {
            if (minion.getId().equals(id)) return this.minions.remove(minion);
        }
        return false;
    }

    public Minion getMinionById(UUID id) {
        for (Minion minion : this.minions) {
            if (minion.getId().equals(id)) return minion;
        }
        return null;
    }

    public List<Minion> getMinions() {
        return List.copyOf(this.minions);
    }

    public int getTotalUniqueMinions() {
        return this.minionUnlockeds.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getCurrentMinions() {
        return this.minions.size();
    }

    public boolean removeMinion(Minion minion) {
        return this.minions.remove(minion);
    }

    public Minion getMinionByStorage(Location location) {
        for (Minion minion : this.minions) {
            if (minion.getStorage() != null) {
                Location storageLoc = minion.getStorage().location().toLocation();
                if (storageLoc.getWorld().equals(location.getWorld())
                        && storageLoc.getBlockX() == location.getBlockX()
                        && storageLoc.getBlockY() == location.getBlockY()
                        && storageLoc.getBlockZ() == location.getBlockZ()) {
                    return minion;
                }
            }
        }
        return null;
    }

    public double getTotalEarnings() {
        return this.minions.stream().mapToDouble(minion -> {
            MinionModifierData autoSell = minion.getModifiersByAnyType(ModifierType.AUTO_SELL);
            return autoSell == null ? 0.0 : autoSell.getMoneyEarned();
        }).sum();
    }

    public Minion getMinionByIndex(int index) {
        if (index < 0 || index >= this.minions.size()) return null;
        int i = 0;
        for (Minion minion : this.minions) {
            if (i == index) return minion;
            i++;
        }
        return null;
    }

    public UUID getOwner()                          { return this.owner; }
    public String getOwnerName()                    { return this.ownerName; }
    public UUID getViewMinion()                     { return this.viewMinion; }
    public int getMaxMinions()                      { return this.maxMinions; }
    public Map<String, Integer> getMinionUnlockeds(){ return this.minionUnlockeds; }

    public void setViewMinion(UUID id)    { this.viewMinion = id; }
    public void setMaxMinions(int value)  { this.maxMinions = value; }

    @Override
    public String toString() {
        return "PlayerMinions(owner=" + this.getOwner()
                + ", ownerName=" + this.getOwnerName()
                + ", minions=" + this.getMinions()
                + ", viewMinion=" + this.getViewMinion()
                + ", maxMinions=" + this.getMaxMinions()
                + ", minionUnlockeds=" + this.getMinionUnlockeds() + ")";
    }
}
