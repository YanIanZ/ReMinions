package dev.yanianz.reminions.task;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.AnimationConfig;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionModifierData;
import dev.yanianz.reminions.core.minion.MinionSkinModel;
import dev.yanianz.reminions.core.minion.MinionStatus;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.managers.ModifierManager;
import dev.yanianz.reminions.managers.PlayerManager;
import dev.yanianz.reminions.managers.SkinManager;
import dev.yanianz.reminions.menu.impl.MinionMenu;
import dev.yanianz.reminions.menu.impl.StorageMenu;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/**
 * Main per-tick driver for every loaded minion. Runs on the main thread as a {@link BukkitRunnable}.
 *
 * <p>Each invocation processes a fixed-size slice of the world's minions ({@link #BATCH_SIZE})
 * to keep tick time bounded regardless of how many minions are loaded. The next call resumes
 * from where the previous one stopped using the {@link #index} cursor.</p>
 *
 * <p>For each minion the task:</p>
 * <ol>
 *   <li>Updates the set of nearby viewers (so the skin model only spawns when a player is close).</li>
 *   <li>Spawns / despawns the armorstand based on viewer presence.</li>
 *   <li>Consumes "due" production actions accumulated since the last visit.</li>
 *   <li>Picks an action handler matching the minion type (miner / farmer / lumberjack / killer / fisherman).</li>
 *   <li>Falls back to offline-style production when the minion has no nearby viewer (no animation, just inventory mutation).</li>
 * </ol>
 *
 * <p>The fishing animation embeds two nested {@link BukkitRunnable} timers — one for casting,
 * one for reeling the dropped item back to the minion's hand.</p>
 */
public class MinionThreadTask extends BukkitRunnable {

    /** Maximum number of minions processed in a single tick before the cursor advances. */
    private static final int BATCH_SIZE = 150;

    /** A minion's skin model is visible to players within this squared distance (16 blocks). */
    private static final double VIEW_DISTANCE_SQUARED = 256.0;

    /** Soft TTL for {@code MinionConfig.isValid()} cache (in server ticks); ~10 seconds at 20 TPS. */
    private static final long VALID_CACHE_TTL_TICKS = 200L;

    /** Vanilla day-time range when farmer minions stop working. */
    private static final long NIGHT_START_TIME = 13_000L;
    private static final long NIGHT_END_TIME = 23_000L;

    // --- Look / swing animation tuning ---
    private static final int LOOK_INTERPOLATION_STEPS = 10;
    private static final long LOOK_INTERPOLATION_PERIOD_TICKS = 2L;
    private static final int SWING_STEPS_HALF = 8;
    private static final double SWING_ARM_ANGLE_DEGREES = -75.0;
    private static final long SWING_PERIOD_TICKS = 2L;

    // --- Fish animation tuning ---
    private static final long FISH_CAST_PERIOD_TICKS = 5L;
    private static final long FISH_REEL_PERIOD_TICKS = 2L;
    private static final int FISH_CAST_PARTICLE_INTERVAL = 8;
    private static final int FISH_CAST_DURATION_TICKS = 16;
    private static final double FISH_LINE_REACH_BLOCKS = 4.0;
    private static final double FISH_REEL_STEP = 0.1;
    private static final double FISH_REEL_TARGET = 1.0;
    private static final double CATCH_FISH_PROB_COD = 0.5;
    private static final double CATCH_FISH_PROB_SALMON = 0.8;
    private static final double CATCH_FISH_PROB_TROPICAL = 0.95;

    // --- Hand offset (in armor-stand local space, before yaw rotation). ---
    private static final Vector HAND_OFFSET = new Vector(0.18, 0.65, 0.0);
    private static final double PARTICLE_LINE_STEP = 0.2;
    private static final double LINE_ARC_AMPLITUDE = -0.5;
    private static final DustOptions FISH_LINE_DUST = new DustOptions(Color.BLACK, 0.4F);

    private static final NamespacedKey VANILLA_EMPTY_LOOT_TABLE = NamespacedKey.minecraft("empty");

    // --- Dependencies (constructor-injected) ---
    private final PlayerManager playerManager;
    private final MinionManager minionManager;
    private final ModifierManager modifierManager;
    private final SkinManager skinManager;

    /** Round-robin batch cursor. Wraps to 0 once it walks past {@code allMinions.size()}. */
    private int index = 0;

    public MinionThreadTask(PlayerManager playerManager, MinionManager minionManager,
                            ModifierManager modifierManager, SkinManager skinManager) {
        this.playerManager = playerManager;
        this.minionManager = minionManager;
        this.modifierManager = modifierManager;
        this.skinManager = skinManager;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Tick loop
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        List<Minion> allMinions = this.playerManager.getAllMinions();
        if (allMinions.isEmpty()) {
            return;
        }

        int batchStart = this.index * BATCH_SIZE;
        if (batchStart >= allMinions.size()) {
            this.index = 0;
            batchStart = 0;
        }
        int batchEnd = Math.min(batchStart + BATCH_SIZE, allMinions.size());

        for (Minion minion : allMinions.subList(batchStart, batchEnd)) {
            this.processMinion(minion);
        }
        this.index++;
    }

    /** Drives the full per-minion lifecycle for one tick. See class-level Javadoc. */
    private void processMinion(Minion minion) {
        this.updateViewers(minion);

        MinionConfig config = this.minionManager.get(minion.getName());
        if (config == null) {
            return;
        }

        boolean hasViewers = !minion.getViewers().isEmpty();

        // If the armorstand was killed externally (e.g. /kill) but viewers exist,
        // despawn so the spawn logic below recreates it.
        if (minion.getSkinModel() != null && minion.getSkinModel().getStand().isDead() && hasViewers) {
            minion.despawn();
        }

        // Spawn-on-view / despawn-when-empty.
        if (hasViewers) {
            if (minion.getSkinModel() == null) {
                minion.spawn(this.skinManager.get(minion.getCurrentSkin()));
            }
        } else if (minion.getSkinModel() != null) {
            minion.despawn();
        }

        long actionsDue = minion.consumeDueActions(
                System.currentTimeMillis(),
                minion.getActionIntervalMillis(minion.getSpeedMultiplier(this.modifierManager)),
                this.getMaxCatchupMillis()
        );
        if (actionsDue <= 0L) {
            return;
        }

        // Refresh the modifier list (drop expired buffs) and repaint the menu if open.
        if (minion.cleanupModifiers()) {
            Player owner = Bukkit.getPlayer(minion.getOwner());
            if (owner != null && owner.getOpenInventory().getTopInventory().getHolder() instanceof MinionMenu menu) {
                menu.updateModifiersSection(minion, config);
            }
        }

        Location minionLoc = minion.getLoc().toLocation();
        if (minionLoc == null) {
            hasViewers = false;
        }

        int workRadius = minion.getBaseRadius() + (int) this.modifierManager.getModifierNumber(minion, ModifierType.RADIUS);
        boolean isValid = this.computeIsValidWithCache(minion, config, minionLoc, workRadius);

        if (hasViewers && !isValid) {
            minion.setStatus(MinionStatus.POSITION_INVALID);
            minion.getSkinModel().addHolograms(MinionStatus.POSITION_INVALID, -1);
            return;
        }

        boolean primaryFull = minion.getInventory().isFull();
        boolean storageFull = minion.getStorage() != null && minion.getStorage().inventory().isFull();
        MinionModifierData autoSellMod = minion.getModifiersByAnyType(ModifierType.AUTO_SELL);

        // Fully blocked: primary inv full, no storage outlet, no auto-sell modifier -> mark FULLY.
        if (primaryFull && (minion.getStorage() == null || storageFull) && autoSellMod == null) {
            minion.setStatus(MinionStatus.FULLY);
            if (hasViewers) {
                minion.getSkinModel().addHolograms(MinionStatus.FULLY, -1);
            }
            return;
        }

        // Active animation path (only when a viewer is present and exactly one action is due).
        if (actionsDue <= 1L && hasViewers) {
            AnimationConfig anim = config.animation();
            switch (minion.getType()) {
                case MINER:      this.handleMiner(minion, config, minionLoc, workRadius, anim, hasViewers, primaryFull, storageFull); break;
                case FARMER:     this.handleFarmer(minion, config, minionLoc, workRadius, anim, hasViewers, primaryFull, storageFull); break;
                case LUMBERJACK: this.handleLumberJack(minion, config, minionLoc, workRadius, anim, hasViewers, primaryFull, storageFull); break;
                case KILLER:     this.handleKiller(minion, config, minionLoc, workRadius, anim, hasViewers, primaryFull, storageFull); break;
                case FISHERMAN:  this.handleFisherman(minion, config, minionLoc, workRadius, anim, hasViewers, primaryFull, storageFull); break;
            }
            return;
        }

        // Offline / no-viewer fast-path: skip animations, batch-apply production.
        long productionCycles = minion.consumeProductionCycles(actionsDue);
        if (productionCycles > 0L && config.workOffline(minion, this.modifierManager, productionCycles)) {
            this.refreshInventoryView(minion);
        }
        if (hasViewers && minion.getSkinModel() != null) {
            minion.getSkinModel().addHolograms(minion.getStatus(), 2);
        }
    }

    /**
     * Returns the cached isValid() result if it is still warm, otherwise re-validates and
     * stores the result. The cache is invalidated by {@code BlockChangeListener} when a
     * nearby block edit fires, plus a soft TTL guards against external changes that did
     * not raise a Bukkit event.
     */
    private boolean computeIsValidWithCache(Minion minion, MinionConfig config, Location loc, int radius) {
        long now = Bukkit.getCurrentTick();
        boolean cacheUsable = minion.validCacheState() != 0
                && minion.validCacheRadius() == radius
                && (now - minion.validCacheTickStamp()) < VALID_CACHE_TTL_TICKS;
        if (cacheUsable) {
            return minion.validCacheState() == 1;
        }
        boolean valid = config.isValid(loc, radius);
        minion.recordValidCache(valid, radius, now);
        return valid;
    }

    /** Maximum wall-clock the minion is allowed to "catch up" while the owner was offline. */
    private long getMaxCatchupMillis() {
        var config = ReMinions.getPlugin().getConfig0();
        if (!config.getBoolean("settings.minions_offline_mode", true)) {
            return 0L;
        }
        int days = config.getInt("settings.minions_offline_time_limit", -1);
        return days <= 0 ? 0L : TimeUnit.DAYS.toMillis(days);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Inventory view refresh — re-renders the owner's open menu after inventory mutates
    // ─────────────────────────────────────────────────────────────────────────────

    private void updateInventoryView(Minion minion, MinionConfig config, boolean primaryFull, boolean storageFull) {
        if (config.work(minion, null, this.modifierManager, primaryFull, storageFull)) {
            this.refreshInventoryView(minion);
        }
    }

    private void refreshInventoryView(Minion minion) {
        Player owner = Bukkit.getPlayer(minion.getOwner());
        if (owner == null) {
            return;
        }
        InventoryHolder top = NMSHandlerProvider.getInventoryBridge().getTopHolder(owner);
        if (top instanceof MinionMenu menu && menu.getViewMinion().equals(minion.getId())) {
            menu.populateMinionInventory(minion);
        } else if (top instanceof StorageMenu storageMenu && storageMenu.getViewMinion().equals(minion.getId())) {
            storageMenu.populateMinionInventory(minion);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Action primitives — block edit + entity attack with smooth animation
    // ─────────────────────────────────────────────────────────────────────────────

    private void performBlockAction(Minion minion, MinionStatus status,
                                    Supplier<Location> targetSupplier, Consumer<Block> mutate,
                                    boolean snapBackAfter) {
        Location target = targetSupplier.get();
        if (target == null) {
            minion.setStatus(MinionStatus.POSITION_INVALID);
            minion.getSkinModel().addHolograms(MinionStatus.POSITION_INVALID, -1);
            return;
        }
        ArmorStand stand = minion.getSkinModel().getStand();
        float originalPitch = stand.getPitch();
        float originalYaw = stand.getYaw();
        this.lookAtBlock(target, stand, () -> this.swingArm(stand, () -> {
            minion.getSkinModel().addHolograms(status, 2);
            mutate.accept(target.getBlock());
            if (snapBackAfter) {
                this.resetLook(stand, originalYaw, originalPitch, null);
            }
            DebugLogger.debug("⛏ Minion " + minion.getId() + " did action " + status + " in " + target);
        }));
    }

    private void performEntityAction(Minion minion, Supplier<LivingEntity> targetSupplier, Consumer<LivingEntity> attack) {
        LivingEntity target = targetSupplier.get();
        if (target == null) {
            return;
        }
        ArmorStand stand = minion.getSkinModel().getStand();
        float originalPitch = stand.getPitch();
        float originalYaw = stand.getYaw();
        this.lookAtBlock(target.getLocation(), stand, () -> this.swingArm(stand, () -> {
            minion.getSkinModel().addHolograms(MinionStatus.WORKING_BREAK, 2);
            attack.accept(target);
            this.resetLook(stand, originalYaw, originalPitch, null);
            DebugLogger.debug("⚔ Minion " + minion.getId() + " attacks " + target.getType());
        }));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Two-phase work loops shared by miner / farmer / lumberjack / fisherman
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Toggles a minion between BREAK and PLACE phases. {@code canPerform=false} just
     * advances the state machine without animating (e.g. nothing to harvest).
     */
    private void handleWork(Minion minion, MinionConfig config,
                            boolean primaryFull, boolean storageFull,
                            boolean canPerform, boolean hasViewers,
                            Supplier<Location> breakTarget, Supplier<Location> placeTarget,
                            Consumer<Block> breakAction, Consumer<Block> placeAction) {
        if (!canPerform) {
            if (minion.getStatus() != MinionStatus.POSITION_INVALID) {
                if (minion.getStatus() == MinionStatus.WORKING_BREAK) {
                    minion.setStatus(MinionStatus.WORKING_PLACE);
                } else {
                    minion.setStatus(MinionStatus.WORKING_BREAK);
                    this.updateInventoryView(minion, config, primaryFull, storageFull);
                }
            }
            return;
        }
        if (primaryFull) {
            minion.setStatus(MinionStatus.WORKING_BREAK);
            this.updateInventoryView(minion, config, primaryFull, storageFull);
            this.performBlockAction(minion, MinionStatus.WORKING_BREAK, breakTarget, breakAction, true);
        } else {
            minion.setStatus(MinionStatus.WORKING_PLACE);
            this.performBlockAction(minion, MinionStatus.WORKING_PLACE, placeTarget, placeAction, true);
        }
    }

    /** Fishing variant: the place phase does not snap-back so the line stays drawn. */
    private void handleWorkFish(Minion minion, MinionConfig config,
                                boolean primaryFull, boolean storageFull,
                                boolean canPerform, boolean hasViewers,
                                Supplier<Location> breakTarget, Supplier<Location> placeTarget,
                                Consumer<Block> breakAction, Consumer<Block> placeAction) {
        if (primaryFull) {
            minion.setStatus(MinionStatus.WORKING_BREAK);
            this.updateInventoryView(minion, config, primaryFull, storageFull);
            if (hasViewers && canPerform) {
                this.performBlockAction(minion, MinionStatus.WORKING_BREAK, breakTarget, breakAction, false);
            }
        } else {
            minion.setStatus(MinionStatus.WORKING_PLACE);
            if (hasViewers && canPerform) {
                this.performBlockAction(minion, MinionStatus.WORKING_PLACE, placeTarget, placeAction, true);
            }
        }
    }

    /** Killer variant: BREAK phase calls {@code performEntityAction} instead of block edit. */
    private void handleWorkEntity(Minion minion, MinionConfig config,
                                  boolean primaryFull, boolean storageFull,
                                  boolean canPerform, boolean hasViewers,
                                  Supplier<LivingEntity> entityTarget, Supplier<Location> spawnTarget,
                                  Consumer<LivingEntity> attack, Consumer<Location> spawn) {
        if (primaryFull) {
            minion.setStatus(MinionStatus.WORKING_BREAK);
            this.updateInventoryView(minion, config, primaryFull, storageFull);
            if (hasViewers && canPerform) {
                this.performEntityAction(minion, entityTarget, attack);
            }
        } else {
            minion.setStatus(MinionStatus.WORKING_PLACE);
            if (hasViewers && canPerform) {
                this.performBlockAction(minion, MinionStatus.WORKING_PLACE, () -> {
                    Location spot = spawnTarget.get();
                    return spot != null ? spot.getBlock().getRelative(BlockFace.UP).getLocation() : null;
                }, blk -> spawn.accept(blk.getLocation()), true);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Per-type handlers
    // ─────────────────────────────────────────────────────────────────────────────

    private void handleFisherman(Minion minion, MinionConfig config, Location loc, int radius,
                                 AnimationConfig anim, boolean hasViewers, boolean primaryFull, boolean storageFull) {
        this.handleWorkFish(
                minion, config, primaryFull, storageFull,
                config.areAllBlocksAllowed(loc, radius), hasViewers,
                () -> config.findFirstBlock(loc, radius, null, Material.WATER),
                () -> config.findFirstBlock(loc, radius, null, Material.AIR),
                waterBlock -> this.catchFish(minion.getSkinModel(), waterBlock, anim.entityParticleCatch()),
                airBlock -> airBlock.setType(Material.WATER)
        );
    }

    private void handleKiller(Minion minion, MinionConfig config, Location loc, int radius,
                              AnimationConfig anim, boolean hasViewers, boolean primaryFull, boolean storageFull) {
        if (hasViewers && !config.areAllBlocksAllowed(loc, radius)) {
            minion.setStatus(MinionStatus.POSITION_INVALID);
            minion.getSkinModel().addHolograms(MinionStatus.POSITION_INVALID, -1);
            return;
        }
        this.handleWorkEntity(
                minion, config, primaryFull, storageFull,
                config.getEntity(loc, radius, anim.entityKill()) != null, hasViewers,
                () -> config.getEntity(loc, radius, anim.entityKill()),
                () -> config.findFirstBlock(loc, radius, null),
                victim -> {
                    if (victim instanceof Mob mob) {
                        mob.setLootTable(Bukkit.getLootTable(VANILLA_EMPTY_LOOT_TABLE));
                    }
                    victim.getWorld().spawnParticle(anim.entityParticleKill(), victim.getLocation(), 1);
                    victim.damage(victim.getHealth() + 1.0);
                },
                spawnAt -> {
                    LivingEntity spawned = (LivingEntity) spawnAt.getWorld().spawnEntity(
                            spawnAt.add(0.5, 0.0, 0.5), anim.entityKill());
                    spawned.getPersistentDataContainer().set(dev.yanianz.reminions.Keys.ENTITY_MINION, PersistentDataType.STRING, "");
                }
        );
    }

    private void handleLumberJack(Minion minion, MinionConfig config, Location loc, int radius,
                                  AnimationConfig anim, boolean hasViewers, boolean primaryFull, boolean storageFull) {
        if (hasViewers && !config.areAllBlocksAllowed(loc, radius)) {
            minion.setStatus(MinionStatus.POSITION_INVALID);
            minion.getSkinModel().addHolograms(MinionStatus.POSITION_INVALID, -1);
            return;
        }
        this.handleWork(
                minion, config, primaryFull, storageFull,
                config.areAllTrees(loc, radius, anim.heightLog(), anim.logTree(), anim.leaveTree()),
                hasViewers,
                () -> config.findFirstBlock(loc, radius, blk -> config.isTree(blk.getLocation(), anim.heightLog(), anim.logTree(), anim.leaveTree())),
                () -> config.findTreeSpotOnPerimeter(loc, radius, anim.heightLog()),
                blk -> config.destroyTree(blk.getRelative(BlockFace.UP), anim.heightLog()),
                blk -> config.generateTree(blk.getRelative(BlockFace.UP), anim.logTree(), anim.leaveTree(), anim.fruitTree(), anim.heightLog())
        );
    }

    private void handleFarmer(Minion minion, MinionConfig config, Location loc, int radius,
                              AnimationConfig anim, boolean hasViewers, boolean primaryFull, boolean storageFull) {
        if (this.isFarmerSleeping(loc)) {
            minion.setStatus(MinionStatus.IDLE);
            if (hasViewers && minion.getSkinModel() != null) {
                minion.getSkinModel().addHolograms(MinionStatus.IDLE, -1);
            }
            return;
        }
        this.handleWork(
                minion, config, primaryFull, storageFull,
                config.areAllBlockSpecific(loc, radius, anim.crop(), false, new int[]{0, 0, 0}, this.notStorageBlock(minion)),
                hasViewers,
                () -> config.findFirstBlock(loc, radius, null, Material.FARMLAND),
                () -> config.findFirstBlock(loc, radius, this::isFarmableSpot,
                        Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.AIR, Material.FARMLAND),
                blk -> blk.getRelative(BlockFace.UP).setType(Material.AIR),
                blk -> this.farmerPlant(blk, minion, config, anim)
        );
    }

    private void handleMiner(Minion minion, MinionConfig config, Location loc, int radius,
                             AnimationConfig anim, boolean hasViewers, boolean primaryFull, boolean storageFull) {
        this.handleWork(
                minion, config, primaryFull, storageFull,
                config.areAllBlocksAllowed(loc, radius), hasViewers,
                () -> config.findFirstBlock(loc, radius, null, anim.block()),
                () -> config.findFirstBlock(loc, radius, null, Material.AIR),
                blk -> blk.setType(Material.AIR),
                blk -> blk.setType(anim.block())
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Farmer helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} while the farmer should be inactive (night-time + feature flag enabled).
     * Vanilla day-time: 0..11999 = day, 12000..12999 = sunset, 13000..22999 = night, 23000..23999 = sunrise.
     */
    private boolean isFarmerSleeping(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!ReMinions.getPlugin().getConfig0().getBoolean("settings.farmer_night_inactive", true)) {
            return false;
        }
        long time = loc.getWorld().getTime();
        return time >= NIGHT_START_TIME && time < NIGHT_END_TIME;
    }

    /** A position is farmable if it's already farmland with an air block above. */
    private boolean isFarmableSpot(Block block) {
        if (block.getType() != Material.FARMLAND) {
            return true;
        }
        return block.getRelative(BlockFace.UP).getType() == Material.AIR;
    }

    /** Refuse to overwrite the minion's own storage block while scanning for crop tiles. */
    private java.util.function.Predicate<Location> notStorageBlock(Minion minion) {
        return target -> {
            MinionStorage storage = minion.getStorage();
            if (storage == null) {
                return false;
            }
            Location storageLoc = storage.location().toLocation();
            return storageLoc.getBlockX() == target.getBlockX()
                    && storageLoc.getBlockY() == target.getBlockY()
                    && storageLoc.getBlockZ() == target.getBlockZ();
        };
    }

    /** Convert the targeted dirt-ish block into farmland and plant the configured crop above. */
    private void farmerPlant(Block dirtBlock, Minion minion, MinionConfig config, AnimationConfig anim) {
        MinionStorage storage = minion.getStorage();
        Block cropBlock = dirtBlock.getRelative(BlockFace.UP);
        if (storage != null) {
            Location storageLoc = storage.location().toLocation();
            Location cropLoc = cropBlock.getLocation();
            if (storageLoc.getBlockX() == cropLoc.getBlockX()
                    && storageLoc.getBlockY() == cropLoc.getBlockY()
                    && storageLoc.getBlockZ() == cropLoc.getBlockZ()) {
                return;
            }
        }
        if (dirtBlock.getType() != Material.FARMLAND) {
            dirtBlock.setType(Material.FARMLAND);
        }
        cropBlock.setType(anim.crop());
        config.plantCrop(cropBlock, anim.cropParticle(), () -> {});
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Viewer set maintenance
    // ─────────────────────────────────────────────────────────────────────────────

    /** Keeps {@code Minion#getViewers()} in sync with players within view range in the same world. */
    private void updateViewers(Minion minion) {
        Location loc = minion.getLoc().toLocation();
        if (loc == null) {
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        // Add players that walked into range.
        for (Player player : world.getPlayers()) {
            if (!player.isOnline()) continue;
            if (player.getLocation().distanceSquared(loc) > VIEW_DISTANCE_SQUARED) continue;
            UUID playerId = player.getUniqueId();
            if (minion.addView(playerId)) {
                DebugLogger.debug("👀 Player " + player.getName() + " now sees the minion " + minion.getId());
            }
        }

        // Drop viewers that left range / world / disconnected.
        Iterator<UUID> it = minion.getViewers().iterator();
        while (it.hasNext()) {
            UUID viewerId = it.next();
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()
                    || !viewer.getWorld().equals(world)
                    || viewer.getLocation().distanceSquared(loc) > VIEW_DISTANCE_SQUARED) {
                it.remove();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Smooth head-look + arm-swing tweens
    // ─────────────────────────────────────────────────────────────────────────────

    /** Turns the armorstand's head toward {@code target}, then runs {@code afterLook}. */
    public void lookAtBlock(Location target, ArmorStand stand, Runnable afterLook) {
        if (target == null || stand == null || stand.isDead()) {
            return;
        }
        Location standLoc = stand.getLocation();
        double dx = target.getX() - standLoc.getX();
        double dy = target.getY() + 0.5 - (standLoc.getY() + stand.getEyeHeight());
        double dz = target.getZ() - standLoc.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizDist));
        this.smoothLook(stand, yaw, pitch, afterLook);
    }

    public void resetLook(ArmorStand stand, float yaw, float pitch, Runnable afterLook) {
        if (stand != null && !stand.isDead()) {
            this.smoothLook(stand, yaw, pitch, afterLook);
        }
    }

    /** Linear interpolation of yaw + pitch over {@link #LOOK_INTERPOLATION_STEPS} ticks. */
    private void smoothLook(final ArmorStand stand, final float targetYaw, final float targetPitch, final Runnable afterLook) {
        Location startLoc = stand.getLocation();
        final float yawStep = (targetYaw - startLoc.getYaw()) / (float) LOOK_INTERPOLATION_STEPS;
        final float pitchStep = (targetPitch - startLoc.getPitch()) / (float) LOOK_INTERPOLATION_STEPS;
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (stand.isDead() || !stand.isValid()) {
                    this.cancel();
                    return;
                }
                Location current = stand.getLocation();
                current.setYaw(current.getYaw() + yawStep);
                current.setPitch(current.getPitch() + pitchStep);
                stand.teleport(current);
                if (++this.step >= LOOK_INTERPOLATION_STEPS) {
                    current.setYaw(targetYaw);
                    current.setPitch(targetPitch);
                    stand.teleport(current);
                    this.cancel();
                    if (afterLook != null) {
                        afterLook.run();
                    }
                }
            }
        }.runTaskTimer(ReMinions.getPlugin(), 0L, LOOK_INTERPOLATION_PERIOD_TICKS);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Fishing animation (cast line, wait, drop fish, reel item to hand)
    // ─────────────────────────────────────────────────────────────────────────────

    private void catchFish(MinionSkinModel skinModel, Block waterBlock, Particle catchParticle) {
        ArmorStand stand = skinModel.getStand();
        if (stand == null || stand.isDead()) {
            return;
        }
        ItemStack mainHand = stand.getEquipment().getItemInMainHand();
        if (mainHand.getType() != Material.FISHING_ROD) {
            DebugLogger.debug("🎣 Minion doesn't have a fishing rod in his hand.");
            return;
        }

        this.swingArm(stand, () -> {
            final World world = stand.getWorld();
            Location handLoc = this.getHandLocation(stand);
            // Project the line tip a few blocks ahead of the hand.
            final Location lineTip = handLoc.clone().add(handLoc.getDirection().normalize().multiply(FISH_LINE_REACH_BLOCKS));
            this.drawCurvedLine(world, handLoc, lineTip, FISH_LINE_DUST);

            new BukkitRunnable() {
                int timer = 0;

                @Override
                public void run() {
                    if (stand.isDead() || !stand.isValid()) {
                        this.cancel();
                        return;
                    }
                    this.timer++;
                    Location currentHand = MinionThreadTask.this.getHandLocation(stand);
                    MinionThreadTask.this.drawCurvedLine(world, currentHand, lineTip, FISH_LINE_DUST);

                    if (this.timer % FISH_CAST_PARTICLE_INTERVAL == 0) {
                        world.spawnParticle(catchParticle, lineTip, 8, 0.2, 0.1, 0.2, 0.01);
                    }
                    if (this.timer <= FISH_CAST_DURATION_TICKS) {
                        return;
                    }

                    // Splash + drop a random fish at the line tip, then reel it toward the hand.
                    world.spawnParticle(NMSHandlerProvider.getParticleBridge().splashParcicle(),
                            lineTip, 20, 0.3, 0.2, 0.3, 0.05);
                    ItemStack fishItem = MinionThreadTask.this.getRandomFish();
                    final Item caughtFish = world.dropItem(lineTip, fishItem);
                    caughtFish.setVelocity(new Vector(0.0, 0.25, 0.0));
                    DebugLogger.debug("🐟 Minion caught a " + fishItem.getType() + " in " + lineTip);

                    new BukkitRunnable() {
                        double progress = 0.0;

                        @Override
                        public void run() {
                            if (stand.isDead() || !stand.isValid() || caughtFish.isDead()) {
                                this.cancel();
                                return;
                            }
                            this.progress += FISH_REEL_STEP;
                            if (this.progress >= FISH_REEL_TARGET) {
                                caughtFish.remove();
                                this.cancel();
                                return;
                            }
                            Location targetHand = MinionThreadTask.this.getHandLocation(stand);
                            Vector lineToHand = targetHand.toVector().subtract(lineTip.toVector());
                            Vector lerped = lineTip.toVector().clone().add(lineToHand.multiply(this.progress));
                            caughtFish.teleport(new Location(world, lerped.getX(), lerped.getY(), lerped.getZ()));
                            MinionThreadTask.this.drawCurvedLine(world, targetHand, caughtFish.getLocation(), FISH_LINE_DUST);
                        }
                    }.runTaskTimer(ReMinions.getPlugin(), 0L, FISH_REEL_PERIOD_TICKS);
                    this.cancel();
                }
            }.runTaskTimer(ReMinions.getPlugin(), 0L, FISH_CAST_PERIOD_TICKS);
        });
    }

    /** Approximate world-space position of the armorstand's right hand. */
    private Location getHandLocation(ArmorStand stand) {
        Location base = stand.getLocation().clone();
        Vector offset = this.rotateVector(HAND_OFFSET.clone(), base.getYaw());
        return base.add(offset);
    }

    /** Rotates {@code v} around the Y axis by {@code yawDegrees} (so it follows the head yaw). */
    private Vector rotateVector(Vector v, float yawDegrees) {
        double rad = Math.toRadians(-yawDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double nx = v.getX() * cos - v.getZ() * sin;
        double nz = v.getX() * sin + v.getZ() * cos;
        return new Vector(nx, v.getY(), nz);
    }

    /** Spawns dust-particle steps following a low arc from {@code start} to {@code end}. */
    private void drawCurvedLine(World world, Location start, Location end, DustOptions dust) {
        Vector delta = end.toVector().subtract(start.toVector());
        double length = delta.length();
        int steps = (int) (length / PARTICLE_LINE_STEP);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vector pos = start.toVector().clone().add(delta.clone().multiply(t));
            double arcY = LINE_ARC_AMPLITUDE * Math.sin(Math.PI * t);
            pos.setY(pos.getY() + arcY);
            Location particleLoc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
            world.spawnParticle(NMSHandlerProvider.getParticleBridge().dustParticle(), particleLoc, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    /** Random fishing-rod result: 50% cod, 30% salmon, 15% tropical, 5% pufferfish. */
    private ItemStack getRandomFish() {
        double roll = Math.random();
        if (roll < CATCH_FISH_PROB_COD)      return new ItemStack(Material.COD);
        if (roll < CATCH_FISH_PROB_SALMON)   return new ItemStack(Material.SALMON);
        if (roll < CATCH_FISH_PROB_TROPICAL) return new ItemStack(Material.TROPICAL_FISH);
        return new ItemStack(Material.PUFFERFISH);
    }

    /**
     * Swings the armorstand's right arm forward and back, then runs {@code afterSwing}. Used as
     * a "hit" gesture wrapped around every block / entity action.
     */
    public void swingArm(final ArmorStand stand, final Runnable afterSwing) {
        if (stand == null || stand.isDead()) {
            return;
        }
        final EulerAngle startPose = stand.getRightArmPose();
        final EulerAngle endPose = startPose.add(Math.toRadians(SWING_ARM_ANGLE_DEGREES), 0.0, 0.0);
        final int steps = SWING_STEPS_HALF;
        final double dx = (endPose.getX() - startPose.getX()) / steps;
        final double dy = (endPose.getY() - startPose.getY()) / steps;
        final double dz = (endPose.getZ() - startPose.getZ()) / steps;
        new BukkitRunnable() {
            int step = 0;
            boolean returning = false;

            @Override
            public void run() {
                if (stand.isDead() || !stand.isValid()) {
                    this.cancel();
                    return;
                }
                EulerAngle current = stand.getRightArmPose();
                EulerAngle next = this.returning
                        ? current.add(-dx, -dy, -dz)
                        : current.add(dx, dy, dz);
                stand.setRightArmPose(next);
                if (++this.step >= steps) {
                    if (!this.returning) {
                        this.returning = true;
                        this.step = 0;
                    } else {
                        this.cancel();
                        if (afterSwing != null) {
                            afterSwing.run();
                        }
                    }
                }
            }
        }.runTaskTimer(ReMinions.getPlugin(), 0L, SWING_PERIOD_TICKS);
    }
}
