package dev.yanianz.reminions.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.config.ModifierConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionMeta;
import dev.yanianz.reminions.core.minion.MinionModifierData;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.minion.MinionType;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.core.modifier.AutoSellPricing;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.managers.ModifierManager;
import dev.yanianz.reminions.managers.PlayerManager;
import dev.yanianz.reminions.managers.SkinManager;
import dev.yanianz.reminions.utils.Location3f;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Public, dependency-light entry point for plugins that want to interact with ReMinions at
 * runtime. Exposed as {@code ReMinions.getApi()}.
 *
 * <p>Methods on this class are intended to remain ABI-stable across patch versions — fields
 * stay private, side-effects are documented, and {@code null} returns indicate "no such entity"
 * rather than throwing. Anything not listed here is internal and may change without notice.</p>
 */
public final class BoboAPI {

    private final Config config;
    private final PlayerManager playerManager;
    private final MinionManager minionManager;
    private final SkinManager skinManager;
    private final ModifierManager modifierManager;

    public BoboAPI(Config config, PlayerManager playerManager, MinionManager minionManager,
                   SkinManager skinManager, ModifierManager modifierManager) {
        this.config = config;
        this.playerManager = playerManager;
        this.minionManager = minionManager;
        this.skinManager = skinManager;
        this.modifierManager = modifierManager;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Minion lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /** Creates and registers a minion for {@code ownerId}. Returns {@code null} on misconfigured args. */
    public @Nullable Minion createMinion(String skinId, UUID ownerId, Location location, MinionMeta meta) {
        PlayerMinions playerMinions = this.playerManager.getById(ownerId);
        if (playerMinions == null) return null;
        MinionConfig minionConfig = this.minionManager.get(meta.getId());
        if (minionConfig == null) return null;
        MinionUpgrade upgrade = minionConfig.getUpgrade(meta.getLevel());
        if (upgrade == null) return null;
        MinionSkinConfig skin = this.skinManager.get(skinId);
        if (skin == null) return null;

        MinionInventory inventory = new MinionInventory(new ArrayList<>(), meta.getCollected());
        Minion minion = new Minion(UUID.randomUUID(), meta.getId(), ownerId,
                minionConfig.type(), inventory, minionConfig, new Location3f(location));
        inventory.setMaxSlots(upgrade.maxStorage());
        minion.setLevel(meta.getLevel());
        minion.setSkinLevel(skinId);
        minion.setCacheProductionSpeed(minionConfig.getProductionSpeed(meta.getLevel()));
        minion.setLastGenerated(System.currentTimeMillis());
        this.playerManager.addMinion(playerMinions, minion);
        minionConfig.updateUniqueMinions(playerMinions, this.config, null, meta.getLevel());
        return minion;
    }

    public @Nullable Minion getMinion(UUID minionId) {
        return this.playerManager.getMinionById(minionId);
    }

    public Optional<Minion> findMinion(UUID minionId) {
        return Optional.ofNullable(this.playerManager.getMinionById(minionId));
    }

    /** Removes the minion (despawn + DB row goes on next save). Returns {@code true} if removed. */
    public boolean removeMinion(UUID ownerId, UUID minionId) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return false;
        this.playerManager.removeMinion(ownerId, minion);
        return true;
    }

    public void despawnMinion(UUID minionId) {
        Minion minion = this.getMinion(minionId);
        if (minion != null) minion.despawn();
    }

    public void spawnMinion(String skinId, UUID minionId) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return;
        MinionSkinConfig skin = this.skinManager.get(skinId);
        if (skin != null) minion.spawn(skin);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    public @Nullable PlayerMinions getPlayerData(UUID ownerId) {
        return this.playerManager.getById(ownerId);
    }

    public Collection<PlayerMinions> getAllPlayers() {
        return this.playerManager.getPlayersSnapshot();
    }

    public List<Minion> getAllMinions() {
        return this.playerManager.getAllMinions();
    }

    public List<Minion> getMinionsByOwner(UUID ownerId) {
        PlayerMinions data = this.getPlayerData(ownerId);
        return data == null ? List.of() : List.copyOf(data.getMinions());
    }

    public List<Minion> getMinionsInWorld(World world) {
        if (world == null) return List.of();
        String worldName = world.getName();
        return this.getAllMinions().stream()
                .filter(m -> m.getLoc() != null && worldName.equals(m.getLoc().getWorldName()))
                .collect(Collectors.toList());
    }

    public List<Minion> getMinionsByType(UUID ownerId, MinionType type) {
        PlayerMinions data = this.getPlayerData(ownerId);
        if (data == null || type == null) return List.of();
        return data.getMinions().stream().filter(m -> m.getType() == type).collect(Collectors.toList());
    }

    public List<Minion> getMinionsByConfigId(UUID ownerId, String configId) {
        PlayerMinions data = this.getPlayerData(ownerId);
        if (data == null || configId == null) return List.of();
        return data.getMinions().stream().filter(m -> configId.equals(m.getName())).collect(Collectors.toList());
    }

    public int getMaxMinions(UUID ownerId) {
        PlayerMinions playerMinions = this.getPlayerData(ownerId);
        return playerMinions != null ? playerMinions.getMaxMinions() : 0;
    }

    public int getCurrentMinions(UUID ownerId) {
        PlayerMinions playerMinions = this.getPlayerData(ownerId);
        return playerMinions != null ? playerMinions.getCurrentMinions() : 0;
    }

    public boolean canPlaceMinion(UUID ownerId) {
        PlayerMinions playerMinions = this.getPlayerData(ownerId);
        return playerMinions != null && playerMinions.getCurrentMinions() < playerMinions.getMaxMinions();
    }

    public double getTotalEarnings(UUID ownerId) {
        PlayerMinions data = this.getPlayerData(ownerId);
        return data == null ? 0.0 : data.getTotalEarnings();
    }

    public int getUniqueMinionsCount(UUID ownerId) {
        PlayerMinions data = this.getPlayerData(ownerId);
        return data == null ? 0 : data.getTotalUniqueMinions();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Skins
    // ─────────────────────────────────────────────────────────────────────────

    public void applySkin(UUID minionId, String skinId) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return;
        MinionSkinConfig skin = this.skinManager.get(skinId);
        if (skin != null) {
            minion.setSkin(skinId);
            minion.despawn();
            minion.spawn(skin);
        }
    }

    public @Nullable MinionSkinConfig getSkin(String skinId) {
        return this.skinManager.get(skinId);
    }

    public List<MinionSkinConfig> getAvailableSkins() {
        return new ArrayList<>(this.skinManager.getAll());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Modifiers
    // ─────────────────────────────────────────────────────────────────────────

    public @Nullable ModifierConfig getModifierConfig(String modifierId) {
        return this.modifierManager.get(modifierId);
    }

    public Collection<ModifierConfig> getAllModifierConfigs() {
        return this.modifierManager.getAll();
    }

    public List<MinionModifierData> getModifiers(UUID minionId) {
        Minion minion = this.getMinion(minionId);
        return minion == null ? List.of() : List.copyOf(minion.getModifiers());
    }

    /** Removes the first modifier on {@code minionId} matching {@code modifierId}. */
    public boolean removeModifier(UUID minionId, String modifierId) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return false;
        MinionModifierData match = minion.getModifiers().stream()
                .filter(m -> m.getName().equalsIgnoreCase(modifierId))
                .findFirst().orElse(null);
        if (match == null) return false;
        minion.removeModifier(match);
        return true;
    }

    /**
     * Computes the effective auto-sell price multiplier currently active on the minion (clamped
     * to {@link AutoSellPricing#MAX_MULTIPLIER}). Returns {@code 1.0} when no auto-sell
     * modifier is attached.
     */
    public double getAutoSellPriceMultiplier(UUID minionId) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return 1.0;
        MinionModifierData mod = minion.getModifiersByAnyType(ModifierType.AUTO_SELL);
        return AutoSellPricing.effectiveMultiplier(mod);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Storage
    // ─────────────────────────────────────────────────────────────────────────

    public @Nullable MinionStorage getMinionStorage(UUID minionId) {
        Minion minion = this.getMinion(minionId);
        return minion == null ? null : minion.getStorage();
    }

    public boolean hasStorage(UUID minionId) {
        Minion minion = this.getMinion(minionId);
        return minion != null && minion.getStorage() != null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Counters / progression
    // ─────────────────────────────────────────────────────────────────────────

    public long getCollectedCount(UUID minionId) {
        Minion minion = this.getMinion(minionId);
        return minion == null ? 0L : minion.getCollected();
    }

    public void addCollected(UUID minionId, int amount) {
        Minion minion = this.getMinion(minionId);
        if (minion != null) minion.addCollected(amount);
    }

    public int getMinionLevel(UUID minionId) {
        Minion minion = this.getMinion(minionId);
        return minion == null ? 0 : minion.getLevel();
    }

    /** Sets the minion's level if {@code level} is within the configured upgrade range. */
    public boolean setMinionLevel(UUID minionId, int level) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return false;
        MinionConfig cfg = this.minionManager.get(minion.getName());
        if (cfg == null) return false;
        if (cfg.getUpgrade(level) == null) return false;
        minion.setLevel(level);
        minion.setCacheProductionSpeed(cfg.getProductionSpeed(level));
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Catalog
    // ─────────────────────────────────────────────────────────────────────────

    public @Nullable MinionConfig getMinionConfig(String minionId) {
        return this.minionManager.get(minionId);
    }

    public Collection<MinionConfig> getAllMinionConfigs() {
        return this.minionManager.getAll();
    }
}
