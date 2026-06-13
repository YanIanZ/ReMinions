package dev.yanianz.reminions.core.minion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.managers.ModifierManager;
import dev.yanianz.reminions.utils.Location3f;
import org.jetbrains.annotations.NotNull;

/**
 * Runtime state of a single placed minion.
 *
 * <p>Acts as the unit of work for {@code MinionThreadTask}: holds the worker inventory, the
 * upgrade level, the cosmetic skin, the modifier list, and per-tick caches used by the
 * scheduler (speed multiplier + {@code isValid()} caches).</p>
 *
 * <p>This class is intentionally a plain Java object — JSON-serialised via Gson for DB
 * persistence — so any change to fields here propagates straight into the saved player data.</p>
 */
public class Minion {

    // ─────────────────────────────────────────────────────────────────────────────
    // Tuning constants
    // ─────────────────────────────────────────────────────────────────────────────

    /** Floor on the configured production speed (5 cs ≈ 50 ms) — prevents divide-by-near-zero. */
    private static final double MIN_PRODUCTION_SPEED_SECONDS = 0.05;

    /** Server tick interval in milliseconds; clamps the action interval to ≥ 1 tick. */
    private static final long MIN_ACTION_INTERVAL_MILLIS = 50L;

    /** Snap-to-zero threshold for the per-product remainder map (avoids growing the map indefinitely). */
    private static final double REMAINDER_EPSILON = 1.0E-7;

    /** Sentinel for {@link #lastGenerated} meaning "not initialised yet". */
    private static final long LAST_GENERATED_UNSET = 0L;

    // ─────────────────────────────────────────────────────────────────────────────
    // Persistent identity (serialised to DB)
    // ─────────────────────────────────────────────────────────────────────────────

    private final UUID id;
    private final String name;
    private final UUID owner;
    private final MinionType type;
    private final MinionInventory inventory;
    private MinionConfig minionConfig;
    private MinionStorage storage;
    private final Location3f loc;
    private int level;
    private MinionStatus status;
    private String skin;
    @NotNull
    private String skinLevel;
    private long lastGenerated;
    private long lastConnection;
    private final Map<String, Double> productionRemainders = new HashMap<>();
    private final List<MinionModifierData> modifiers = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────────
    // Transient runtime state (not serialised — recomputed on load)
    // ─────────────────────────────────────────────────────────────────────────────

    private transient MinionSkinModel skinModel;
    private final transient Set<UUID> viewers = new HashSet<>();
    private double cachedSpeedMultiplier = 0.0;
    private double cacheProductionSpeed = 0.0;
    private boolean dirtyCache = true;

    /**
     * Cache for {@code MinionConfig.isValid()} — invalidated by {@code BlockChangeListener}
     * whenever a block is broken / placed within this minion's radius. Lets the per-tick
     * scheduler skip the O(r²) area scan when nothing in range has changed.
     */
    private transient int validCacheState = 0;        // 0 = unknown, 1 = valid, -1 = invalid
    private transient int validCacheRadius = -1;      // radius the cached result was computed for
    private transient long validCacheTickStamp = 0L;  // soft-TTL anchor (server ticks)

    public Minion(UUID id, String name, UUID owner, MinionType type,
                  MinionInventory inventory, MinionConfig minionConfig, Location3f loc) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.type = type;
        this.inventory = inventory;
        this.minionConfig = minionConfig;
        this.loc = loc;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Level
    // ─────────────────────────────────────────────────────────────────────────────

    public void addLevel(int delta)    { this.level += delta; }
    public void removeLevel(int delta) { this.level -= delta; }

    // ─────────────────────────────────────────────────────────────────────────────
    // Modifiers — adding/removing busts the speed multiplier cache
    // ─────────────────────────────────────────────────────────────────────────────

    public void addModifier(MinionModifierData modifier) {
        this.modifiers.add(modifier);
        this.cachedSpeedMultiplier = 0.0;
        this.dirtyCache = true;
    }

    public void removeModifier(MinionModifierData modifier) {
        this.modifiers.remove(modifier);
        this.cachedSpeedMultiplier = 0.0;
        this.dirtyCache = true;
    }

    /** Drops every expired modifier in one pass. Returns {@code true} when at least one was removed. */
    public boolean cleanupModifiers() {
        boolean changed = this.modifiers.removeIf(MinionModifierData::isExpired);
        if (changed) {
            this.dirtyCache = true;
            this.cachedSpeedMultiplier = 0.0;
        }
        return changed;
    }

    public MinionModifierData getModifierById(UUID id) {
        return this.modifiers.stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
    }

    public List<MinionModifierData> getModifiersByType(ModifierType type) {
        return this.modifiers.stream().filter(m -> m.getType() == type).toList();
    }

    /** First modifier of the given type, or {@code null} if none. */
    public MinionModifierData getModifiersByAnyType(ModifierType type) {
        return this.modifiers.stream().filter(m -> m.getType() == type).findFirst().orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Spawn / despawn (transient armorstand model)
    // ─────────────────────────────────────────────────────────────────────────────

    public void spawn(MinionSkinConfig skinConfig) {
        this.skinModel = new MinionSkinModel(skinConfig);
        this.skinModel.spawn(this.loc.toLocation(), this.id);
    }

    public void despawn() {
        if (this.skinModel != null) {
            this.skinModel.despawn();
            this.skinModel = null;
        }
    }

    public String getCurrentSkin() {
        return this.skin != null ? this.skin : this.skinLevel;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Collected counter (delegated to inventory)
    // ─────────────────────────────────────────────────────────────────────────────

    public void addCollected(int amount)       { this.inventory.addCollected(amount); }
    public long getCollected()                 { return this.inventory.getCollected(); }
    public void setCollected(long amount)      { this.inventory.setCollected(amount); }

    // ─────────────────────────────────────────────────────────────────────────────
    // Viewer set (players currently within view range)
    // ─────────────────────────────────────────────────────────────────────────────

    public boolean addView(UUID playerId) { return this.viewers.add(playerId); }
    public void removeView(UUID playerId) { this.viewers.remove(playerId); }

    // ─────────────────────────────────────────────────────────────────────────────
    // Production timing
    // ─────────────────────────────────────────────────────────────────────────────

    /** Convenience: has this minion accumulated enough wall-clock to fire at least one action? */
    public boolean canGenerate(double speedMultiplier) {
        long now = System.currentTimeMillis();
        long intervalMs = this.getActionIntervalMillis(speedMultiplier);
        return now - this.lastGenerated >= intervalMs;
    }

    /**
     * Returns the wall-clock interval in milliseconds between two production actions, given the
     * caller's resolved {@code speedMultiplier}. Floor: 50 ms (one server tick).
     */
    public long getActionIntervalMillis(double speedMultiplier) {
        double secondsPerAction = Math.max(MIN_PRODUCTION_SPEED_SECONDS, this.getProductionSpeed());
        double safeMultiplier = Math.max(0.0, speedMultiplier);
        return Math.max(MIN_ACTION_INTERVAL_MILLIS,
                Math.round(secondsPerAction / (1.0 + safeMultiplier) * 1000.0));
    }

    /**
     * Computes how many production actions have accumulated since {@code lastGenerated} and
     * advances the internal cursor. {@code maxCatchupMs} caps catch-up after long offline gaps.
     *
     * @return number of due actions (≥ 0).
     */
    public long consumeDueActions(long nowMs, long intervalMs, long maxCatchupMs) {
        if (intervalMs <= 0L) {
            return 0L;
        }
        if (this.lastGenerated == LAST_GENERATED_UNSET || this.lastGenerated > nowMs) {
            // First time seen or clock skew — anchor to now and emit nothing this tick.
            this.lastGenerated = nowMs;
            return 0L;
        }

        long elapsed = nowMs - this.lastGenerated;
        if (maxCatchupMs > 0L && elapsed > maxCatchupMs) {
            this.lastGenerated = nowMs - maxCatchupMs;
            elapsed = maxCatchupMs;
        }
        long due = elapsed / intervalMs;
        if (due <= 0L) {
            return 0L;
        }
        this.lastGenerated += due * intervalMs;
        return due;
    }

    /**
     * Splits {@code dueActions} into "break" and "place" half-cycles and updates {@link #status}
     * to the phase the minion is finishing on. Returns how many full production cycles fit
     * into the action count.
     */
    public long consumeProductionCycles(long dueActions) {
        if (dueActions <= 0L) {
            return 0L;
        }
        boolean startedOnBreak = this.status != MinionStatus.WORKING_BREAK;
        long cycles = startedOnBreak ? (dueActions + 1L) / 2L : dueActions / 2L;
        boolean endsOnBreak = (dueActions & 1L) == 1L ? startedOnBreak : !startedOnBreak;
        this.status = endsOnBreak ? MinionStatus.WORKING_BREAK : MinionStatus.WORKING_PLACE;
        return cycles;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Production remainder map (fractional output carried across runs)
    // ─────────────────────────────────────────────────────────────────────────────

    public double getProductionRemainder(String productId) {
        return this.productionRemainders.getOrDefault(productId, 0.0);
    }

    public void setProductionRemainder(String productId, double remainder) {
        if (remainder <= REMAINDER_EPSILON) {
            this.productionRemainders.remove(productId);
        } else {
            this.productionRemainders.put(productId, remainder);
        }
    }

    public double getProductionSpeed()         { return this.cacheProductionSpeed; }

    /** Maximum inventory slots awarded by the current upgrade level (defaults to 1). */
    public int getMaxSlots() {
        MinionUpgrade upgrade = this.minionConfig.getUpgrade(this.level);
        return upgrade == null ? 1 : upgrade.maxStorage();
    }

    public void markGenerated() {
        this.lastGenerated = System.currentTimeMillis();
    }

    /** Seconds since the last successful generation (used by placeholders). */
    public long getTimeLastGeneratedForSeconds() {
        return (System.currentTimeMillis() - this.lastGenerated) / 1000L;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Speed multiplier cache (refreshed lazily by modifier listeners)
    // ─────────────────────────────────────────────────────────────────────────────

    public double getSpeedMultiplier(ModifierManager modifierMgr) {
        if (this.dirtyCache) {
            this.cachedSpeedMultiplier += modifierMgr.getModifierNumber(this, ModifierType.SPEED);
            this.dirtyCache = false;
        }
        // Booster plugins are queried each call so live activations apply immediately —
        // there's no cache invalidation path for external booster state changes.
        double boost = dev.yanianz.reminions.ReMinions.getPlugin().getBoosterService()
                .multiplier(this.getOwner(), dev.yanianz.reminions.booster.BoostKind.SPEED);
        return this.cachedSpeedMultiplier * boost;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // isValid() cache accessors (driven by BlockChangeListener)
    // ─────────────────────────────────────────────────────────────────────────────

    public int validCacheState()       { return this.validCacheState; }
    public int validCacheRadius()      { return this.validCacheRadius; }
    public long validCacheTickStamp()  { return this.validCacheTickStamp; }

    public void recordValidCache(boolean valid, int radius, long tickStamp) {
        this.validCacheState = valid ? 1 : -1;
        this.validCacheRadius = radius;
        this.validCacheTickStamp = tickStamp;
    }

    public void invalidateValidCache() {
        this.validCacheState = 0;
        this.validCacheRadius = -1;
    }

    public int getBaseRadius() {
        return this.minionConfig.baseRadius();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Plain getters / setters
    // ─────────────────────────────────────────────────────────────────────────────

    public UUID getId()                                  { return this.id; }
    public String getName()                              { return this.name; }
    public UUID getOwner()                               { return this.owner; }
    public MinionType getType()                          { return this.type; }
    public MinionInventory getInventory()                { return this.inventory; }
    public MinionConfig getMinionConfig()                { return this.minionConfig; }
    public MinionStorage getStorage()                    { return this.storage; }
    public Location3f getLoc()                           { return this.loc; }
    public int getLevel()                                { return this.level; }
    public MinionStatus getStatus()                      { return this.status; }
    public String getSkin()                              { return this.skin; }
    @NotNull public String getSkinLevel()                { return this.skinLevel; }
    public long getLastGenerated()                       { return this.lastGenerated; }
    public long getLastConnection()                      { return this.lastConnection; }
    public Map<String, Double> getProductionRemainders() { return this.productionRemainders; }
    public List<MinionModifierData> getModifiers()       { return this.modifiers; }
    public MinionSkinModel getSkinModel()                { return this.skinModel; }
    public Set<UUID> getViewers()                        { return this.viewers; }
    public double getCachedSpeedMultiplier()             { return this.cachedSpeedMultiplier; }
    public double getCacheProductionSpeed()              { return this.cacheProductionSpeed; }
    public boolean isDirtyCache()                        { return this.dirtyCache; }

    public void setMinionConfig(MinionConfig config)            { this.minionConfig = config; }
    public void setStorage(MinionStorage storage)               { this.storage = storage; }
    public void setLevel(int level)                             { this.level = level; }
    public void setStatus(MinionStatus status)                  { this.status = status; }
    public void setSkin(String skin)                            { this.skin = skin; }
    public void setSkinLevel(@NotNull String skinLevel) {
        if (skinLevel == null) {
            throw new NullPointerException("skinLevel is marked non-null but is null");
        }
        this.skinLevel = skinLevel;
    }
    public void setLastGenerated(long lastGenerated)            { this.lastGenerated = lastGenerated; }
    public void setLastConnection(long lastConnection)          { this.lastConnection = lastConnection; }
    public void setSkinModel(MinionSkinModel model)             { this.skinModel = model; }
    public void setCachedSpeedMultiplier(double multiplier)     { this.cachedSpeedMultiplier = multiplier; }
    public void setCacheProductionSpeed(double speed)           { this.cacheProductionSpeed = speed; }
    public void setDirtyCache(boolean dirty)                    { this.dirtyCache = dirty; }

    @Override
    public String toString() {
        return "Minion(id=" + this.id
                + ", name=" + this.name
                + ", owner=" + this.owner
                + ", type=" + this.type
                + ", inventory=" + this.inventory
                + ", minionConfig=" + this.minionConfig
                + ", storage=" + this.storage
                + ", loc=" + this.loc
                + ", level=" + this.level
                + ", status=" + this.status
                + ", skin=" + this.skin
                + ", skinLevel=" + this.skinLevel
                + ", lastGenerated=" + this.lastGenerated
                + ", lastConnection=" + this.lastConnection
                + ", productionRemainders=" + this.productionRemainders
                + ", modifiers=" + this.modifiers
                + ", skinModel=" + this.skinModel
                + ", viewers=" + this.viewers
                + ", cachedSpeedMultiplier=" + this.cachedSpeedMultiplier
                + ", cacheProductionSpeed=" + this.cacheProductionSpeed
                + ", dirtyCache=" + this.dirtyCache + ")";
    }
}
