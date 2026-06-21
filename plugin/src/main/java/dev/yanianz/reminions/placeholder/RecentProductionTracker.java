package dev.yanianz.reminions.placeholder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import dev.yanianz.reminions.api.events.MinionItemsProduceEvent;
import dev.yanianz.reminions.core.minion.MinionInventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Rolling per-player counter of items produced over a fixed window. Backs the
 * {@code player_recent_*} placeholders so dashboards can display live hourly / daily output
 * without recomputing the deterministic production formula on every PAPI request.
 *
 * <p>Counters are stored as four buckets so a window query collapses to a single sum without
 * locking. The {@link #tick()} pump should be invoked once a minute by the plugin's scheduler
 * to rotate stale buckets out.</p>
 */
public final class RecentProductionTracker implements Listener {

    /** Minute-grained rolling window length. 60 minutes ≈ 1 hour stat. */
    public static final int WINDOW_MINUTES = 60;

    /** Per-owner total item count produced in the current window. */
    private final ConcurrentHashMap<UUID, RollingCounter> ownerCounters = new ConcurrentHashMap<>();

    @EventHandler
    public void onProduce(MinionItemsProduceEvent event) {
        if (event.getResult() != MinionItemsProduceEvent.ResultState.SUCESS) return;
        if (event.getMinion() == null || event.getMinion().getOwner() == null) return;
        int sum = 0;
        for (MinionInventory.ItemData data : event.getItems()) sum += Math.max(0, data.getAmount());
        if (sum == 0) return;
        this.ownerCounters.computeIfAbsent(event.getMinion().getOwner(), id -> new RollingCounter()).add(sum);
    }

    /** Sum of items produced by {@code owner} in the active rolling window. */
    public long countLastHour(UUID owner) {
        RollingCounter counter = this.ownerCounters.get(owner);
        return counter == null ? 0L : counter.sum();
    }

    /** Rotates the rolling window forward by one minute. Call from a 20×60 tick scheduler. */
    public void tick() {
        this.ownerCounters.values().forEach(RollingCounter::tick);
        this.ownerCounters.values().removeIf(RollingCounter::isEmpty);
    }

    /**
     * Per-minute rolling window. Stores {@link #WINDOW_MINUTES} minute buckets in a ring; the
     * {@link #tick()} call advances the cursor and zeros the new slot.
     */
    private static final class RollingCounter {
        private final long[] buckets = new long[WINDOW_MINUTES];
        private int head;

        synchronized void add(int amount) {
            this.buckets[this.head] += amount;
        }

        synchronized void tick() {
            this.head = (this.head + 1) % this.buckets.length;
            this.buckets[this.head] = 0L;
        }

        synchronized long sum() {
            long total = 0L;
            for (long bucket : this.buckets) total += bucket;
            return total;
        }

        synchronized boolean isEmpty() {
            for (long bucket : this.buckets) if (bucket > 0L) return false;
            return true;
        }
    }
}
