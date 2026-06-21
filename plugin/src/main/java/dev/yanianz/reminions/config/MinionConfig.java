package dev.yanianz.reminions.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.api.events.MinionItemsProduceEvent;
import dev.yanianz.reminions.api.events.MinionSellItemsEvent;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionMeta;
import dev.yanianz.reminions.core.minion.MinionModifierData;
import dev.yanianz.reminions.core.minion.MinionType;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.booster.BoostKind;
import dev.yanianz.reminions.core.modifier.AutoSellPricing;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.core.product.Product;
import dev.yanianz.reminions.managers.ModifierManager;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

/**
 * Immutable configuration for a single minion definition (e.g. {@code cobblestone_minion.yml}).
 *
 * <p>This record bundles all per-type behaviour: the work radius, the base products it makes,
 * the upgrade ladder, the cosmetic head item, and the animation parameters (which block /
 * crop / entity it interacts with). It also exposes the runtime work() / workOffline() entry
 * points used by {@code MinionThreadTask} every tick.</p>
 *
 * <p>State is fully immutable — every "mutation" operates on the {@link Minion} runtime
 * object, never on {@code MinionConfig} itself.</p>
 */
public record MinionConfig(
        String id,
        String name,
        MinionType type,
        ItemBuilder minionItem,
        int baseRadius,
        List<Product> products,
        Set<Material> blocksCheckAround,
        boolean bypassLocationCheck,
        AnimationConfig animation,
        Map<Integer, String> skinLevels,
        Map<Integer, MinionUpgrade> upgrades,
        String categorySkin
) {

    // ─────────────────────────────────────────────────────────────────────────────
    // Block-set constants for the lumberjack tree heuristics (kept for reference;
    // the runtime path lets the AnimationConfig override these on a per-minion basis).
    // ─────────────────────────────────────────────────────────────────────────────

    private static final Set<Material> TRUNK_TYPES = Set.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG, Material.CRIMSON_STEM,
            Material.WARPED_STEM
    );
    private static final Set<Material> LEAF_TYPES = Set.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES
    );
    private static final Set<Material> FRUIT_TYPES = Set.of(Material.COCOA, Material.MELON);

    // ─────────────────────────────────────────────────────────────────────────────
    // Tree-shape geometry: 4 leaf blocks at the corners + 4 in the cardinal arms.
    // The 8 perimeter offsets are used both for "tree validation" and "find spot".
    // ─────────────────────────────────────────────────────────────────────────────

    /** Vertical reach above the trunk where leaves & fruit are placed. */
    private static final int TREE_LEAF_HEIGHT_OFFSET = 4;

    /** Maximum number of upgrade-product cascade iterations before bailing out (avoids infinite loops). */
    private static final int MAX_UPGRADE_CASCADE_ITERATIONS = 32;

    /** Tick period of the {@link #plantCrop} growth animation. */
    private static final long CROP_GROWTH_PERIOD_TICKS = 5L;

    /** Vertical search slack above center for entity-targeting bounding boxes. */
    private static final double ENTITY_SEARCH_HEIGHT_ABOVE = 4.0;

    // ─────────────────────────────────────────────────────────────────────────────
    // Lookups & head item builders
    // ─────────────────────────────────────────────────────────────────────────────

    public Product getProductById(String productId) {
        return this.products.stream().filter(p -> p.getId().equals(productId)).findFirst().orElse(null);
    }

    public MinionUpgrade getMaxUpgrade() {
        return this.upgrades.entrySet().stream().max(Entry.comparingByKey()).map(Entry::getValue).orElse(null);
    }

    public int getMaxLevel() {
        return Collections.max(this.upgrades.keySet());
    }

    public MinionUpgrade getUpgrade(int level) {
        return this.upgrades.get(level);
    }

    public String getSkinLevel(int level) {
        return this.skinLevels.get(level);
    }

    public double getProductionSpeed(int level) {
        MinionUpgrade upgrade = this.getUpgrade(level);
        return upgrade == null ? 0.0 : upgrade.productionSpeed();
    }

    /**
     * Builds the cosmetic head item shown in the menu / inventory for a minion at the given level.
     */
    public ItemStack getMinionHead(int level, long collected, int storage, double productionSpeed) {
        String skinLevelKey = this.getSkinLevel(level);
        MinionSkinConfig skin = skinLevelKey == null ? null
                : ReMinions.getPlugin().getSkinManager().get(skinLevelKey);
        if (skin == null) {
            return null;
        }
        return new ItemBuilder(this.minionItem())
                .setHeadTexture(skin.getSlot(EquipmentSlot.HEAD).getHeadTexture())
                .toBuild(
                        List.of(new ItemKey("minion_item", ItemBuilder.GSON.toJson(new MinionMeta(this.id, level)))),
                        "%roman_level%", Text.toRomanLevel(level),
                        "%storage%", storage,
                        "%collected%", collected,
                        "%production_speed%", Text.format1(productionSpeed)
                );
    }

    /** Convenience overload that pulls level / collected / storage / speed from the live {@link Minion}. */
    public ItemStack getMinionHead(Minion minion) {
        int level = minion.getLevel();
        MinionUpgrade upgrade = this.getUpgrade(level);
        if (upgrade == null) {
            return null;
        }
        String skinLevelKey = this.getSkinLevel(level);
        MinionSkinConfig skin = skinLevelKey == null ? null
                : ReMinions.getPlugin().getSkinManager().get(skinLevelKey);
        if (skin == null) {
            return null;
        }
        return new ItemBuilder(this.minionItem())
                .setHeadTexture(skin.getSlot(EquipmentSlot.HEAD).getHeadTexture())
                .toBuild(
                        List.of(new ItemKey("minion_item", ItemBuilder.GSON.toJson(new MinionMeta(minion)))),
                        "%roman_level%", Text.toRomanLevel(level),
                        "%storage%", minion.getInventory().getMaxSlots(),
                        "%collected%", minion.getCollected(),
                        "%production_speed%", Text.format1(minion.getProductionSpeed())
                );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Block-radius validators (used every tick by MinionThreadTask via the isValid cache)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Strict: every non-air block in the radius below the minion must be in {@code blocksCheckAround}. */
    public boolean isValid(Location center, int radius) {
        return this.areAllBlocksAllowed(center, radius, true, null);
    }

    /** Strict + skip: as {@link #isValid(Location, int)} but skips locations matching {@code skip}. */
    public boolean isValid(Location center, int radius, @Nullable Predicate<Location> skip) {
        return this.areAllBlocksAllowed(center, radius, true, skip);
    }

    /** Loose: every non-air block in the radius below the minion must be in {@code blocksCheckAround}. */
    public boolean areAllBlocksAllowed(Location center, int radius) {
        return this.areAllBlocksAllowed(center, radius, false, null);
    }

    /** Loose + skip: as {@link #areAllBlocksAllowed(Location, int)} but skips locations matching {@code skip}. */
    public boolean areAllBlocksAllowed(Location center, int radius, @Nullable Predicate<Location> skip) {
        return this.areAllBlocksAllowed(center, radius, false, skip);
    }

    private boolean areAllBlocksAllowed(Location center, int radius, boolean strict,
                                        @Nullable Predicate<Location> skip) {
        if (center == null) return false;
        World world = center.getWorld();
        if (world == null || this.blocksCheckAround == null || this.blocksCheckAround.isEmpty()) {
            return false;
        }
        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;
        int floorY = center.getBlockY() - 1;

        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (bx == center.getBlockX() && bz == center.getBlockZ()) continue;
                Block block = world.getBlockAt(bx, floorY, bz);
                if (skip != null && skip.test(block.getLocation())) continue;
                Material type = block.getType();
                // AIR is always allowed (terrain gaps don't make a spot invalid).
                if (type == Material.AIR) continue;
                if (!this.blocksCheckAround.contains(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Generic variant of {@link #areAllBlocksAllowed} that targets a specific {@code material}
     * with an additional {@code skipPredicate} (used by farmer to ignore its own storage block)
     * and an arbitrary {@code offset} (xyz) into the lookup column.
     */
    public boolean areAllBlockSpecific(Location center, int radius, Material material,
                                       boolean strict, int[] offset, Predicate<Location> skipPredicate) {
        if (center == null) return false;
        World world = center.getWorld();
        if (world == null) return false;

        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;
        int floorY = center.getBlockY();

        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (bx == center.getBlockX() && bz == center.getBlockZ()) continue;
                Block block = world.getBlockAt(bx + offset[0], floorY + offset[1], bz + offset[2]);
                Material type = block.getType();
                boolean strictAirCheck = !strict || type != Material.AIR;
                if (strictAirCheck && !skipPredicate.test(block.getLocation()) && material != type) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Random pick from all blocks within the radius below the minion that match {@code materials}
     * (or the configured {@code blocksCheckAround} when no explicit list is given) and pass
     * {@code extraFilter}. Returns {@code null} if no candidate exists.
     */
    public Location findFirstBlock(Location center, int radius,
                                   @Nullable Predicate<Block> extraFilter, @Nullable Material... materials) {
        if (center == null) return null;
        World world = center.getWorld();
        if (world == null) return null;

        Set<Material> allowed = materials != null && materials.length != 0 ? Set.of(materials) : this.blocksCheckAround;
        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;
        int floorY = center.getBlockY() - 1;
        List<Location> candidates = new ArrayList<>();

        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (bx == center.getBlockX() && bz == center.getBlockZ()) continue;
                Block block = world.getBlockAt(bx, floorY, bz);
                if (allowed.contains(block.getType()) && (extraFilter == null || extraFilter.test(block))) {
                    candidates.add(block.getLocation());
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Tree heuristics (lumberjack)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Returns the 8 perimeter coordinates around a center for the tree-grid checks. */
    private static int[][] perimeterOffsets(int radius) {
        return new int[][]{
                {-radius, -radius}, {0, -radius}, {radius, -radius},
                {-radius, 0},                     {radius, 0},
                {-radius, radius},  {0, radius},  {radius, radius}
        };
    }

    /** All 8 perimeter spots must contain a tree of the configured trunk/leaf material. */
    public boolean areAllTrees(Location center, int radius, int trunkHeight, Material trunk, Material leaf) {
        if (center == null) return false;
        World world = center.getWorld();
        if (world == null) return false;
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int floorY = center.getBlockY() - 1;
        for (int[] offset : perimeterOffsets(radius)) {
            Location spot = new Location(world, cx + offset[0], floorY, cz + offset[1]);
            if (!this.isTree(spot, trunkHeight, trunk, leaf)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks that {@code spot}+1Y..+trunkHeight is the requested {@code trunk}, and that
     * the leaf cross at {@code spot}+TREE_LEAF_HEIGHT_OFFSET matches {@code leaf}.
     */
    public boolean isTree(Location spot, int trunkHeight, Material trunk, Material leaf) {
        if (spot == null) return false;
        Block trunkBase = spot.getBlock().getRelative(BlockFace.UP);
        if (trunk != Material.AIR) {
            for (int dy = 0; dy < trunkHeight; dy++) {
                if (trunkBase.getRelative(0, dy, 0).getType() != trunk) {
                    return false;
                }
            }
        }
        if (leaf != Material.AIR) {
            Block leafCenter = trunkBase.getRelative(0, trunkHeight, 0);
            if (leafCenter.getType() != leaf) return false;
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (leafCenter.getRelative(face).getType() != leaf) return false;
            }
        }
        return true;
    }

    /** Destroys the trunk column + 5-block leaf cross around the canopy. */
    public void destroyTree(Block trunkBase, int trunkHeight) {
        for (int dy = 0; dy < trunkHeight; dy++) {
            trunkBase.getRelative(0, dy, 0).setType(Material.AIR);
        }
        Block leafCenter = trunkBase.getRelative(0, TREE_LEAF_HEIGHT_OFFSET, 0);
        leafCenter.setType(Material.AIR);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block arm = leafCenter.getRelative(face);
            arm.setType(Material.AIR);
            arm.getRelative(BlockFace.DOWN).setType(Material.AIR);
        }
    }

    /** Spawns a new tree at {@code trunkBase}; optionally plants {@code fruit} hanging from the canopy. */
    public void generateTree(Block trunkBase, Material trunk, Material leaf, @Nullable Material fruit, int trunkHeight) {
        for (int dy = 0; dy < trunkHeight; dy++) {
            trunkBase.getRelative(0, dy, 0).setType(trunk);
        }
        Block leafCenter = trunkBase.getRelative(0, TREE_LEAF_HEIGHT_OFFSET, 0);
        leafCenter.setType(leaf);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            leafCenter.getRelative(face).setType(leaf);
        }
        if (fruit != null) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block fruitBlock = leafCenter.getRelative(face).getRelative(BlockFace.DOWN);
                if (fruitBlock.getType() == Material.AIR) {
                    fruitBlock.setType(fruit);
                    this.plantCrop(fruitBlock, NMSHandlerProvider.getParticleBridge().cropGrowParticle(), () -> {});
                }
            }
        }
    }

    /** Picks the first perimeter spot whose column above is fully {@code AIR}/{@code CAVE_AIR}. */
    public Location findTreeSpotOnPerimeter(Location center, int radius, int trunkHeight) {
        if (center == null) return null;
        World world = center.getWorld();
        if (world == null) return null;
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int floorY = center.getBlockY() - 1;
        for (int[] offset : perimeterOffsets(radius)) {
            Location spot = new Location(world, cx + offset[0], floorY, cz + offset[1]);
            if (this.isSpotFreeForTree(spot, trunkHeight)) {
                return spot;
            }
        }
        return null;
    }

    private boolean isSpotFreeForTree(Location spot, int trunkHeight) {
        if (spot == null) return false;
        Block base = spot.getBlock();
        for (int dy = 1; dy <= trunkHeight; dy++) {
            Material type = base.getRelative(0, dy, 0).getType();
            if (type != Material.AIR && type != Material.CAVE_AIR) {
                return false;
            }
        }
        return true;
    }

    private boolean isLeaf(Block block) {
        return LEAF_TYPES.contains(block.getType());
    }

    private boolean checkFruit(Block block) {
        Material type = block.getType();
        return type == Material.AIR || FRUIT_TYPES.contains(type);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Crop growth animation (farmer)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Increments an {@link Ageable} crop block one age-step per tick until mature, then runs {@code onMature}. */
    public void plantCrop(final Block crop, final Particle growthParticle, final Runnable onMature) {
        if (!(crop.getBlockData() instanceof Ageable ageable)) {
            onMature.run();
            return;
        }
        new BukkitRunnable() {
            int currentAge = ageable.getAge();
            final int maxAge = ageable.getMaximumAge();

            @Override
            public void run() {
                if (this.currentAge < this.maxAge && !crop.getType().isAir()) {
                    crop.getWorld().spawnParticle(growthParticle, crop.getLocation().clone().add(0.5, 0.5, 0.5), 1);
                    this.currentAge++;
                    ageable.setAge(this.currentAge);
                    crop.setBlockData(ageable, true);
                } else {
                    this.cancel();
                    onMature.run();
                }
            }
        }.runTaskTimer(ReMinions.getPlugin(), 0L, CROP_GROWTH_PERIOD_TICKS);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Entity targeting (killer)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Returns the first {@link LivingEntity} of {@code entityType} inside the radius bounding box. */
    public LivingEntity getEntity(Location center, int radius, EntityType entityType) {
        if (center == null) return null;
        World world = center.getWorld();
        if (world == null) return null;
        double minX = center.getX() - radius;
        double minZ = center.getZ() - radius;
        double minY = center.getY();
        double maxX = center.getX() + radius;
        double maxZ = center.getZ() + radius;
        double maxY = center.getY() + ENTITY_SEARCH_HEIGHT_ABOVE;
        for (Entity entity : world.getNearbyEntities(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ))) {
            if (entity instanceof LivingEntity living && entity.getType() == entityType) {
                return living;
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Live work cycle (called every action interval by MinionThreadTask)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Runs one production tick: builds the product map, applies base & upgrade products,
     * flushes any buffered amounts that didn't fit on the first pass, then logs the resulting
     * inventory snapshot.
     *
     * @param minion        the minion being processed
     * @param autoSellMod   modifier instance that authorises auto-sell (or {@code null})
     * @param modifierMgr   manager used to resolve modifier configs
     * @param primaryFull   {@code true} when the minion's main inventory is currently full
     * @param storageFull   {@code true} when the secondary storage inventory is full (or absent)
     * @return {@code true} when at least one item was successfully produced / sold
     */
    public boolean work(Minion minion, MinionModifierData autoSellMod, ModifierManager modifierMgr,
                        boolean primaryFull, boolean storageFull) {
        MinionInventory storageInv = minion.getStorage() != null ? minion.getStorage().inventory() : null;
        MinionInventory snapshotInv;
        boolean useAutoSellTarget = false;
        if (!primaryFull) {
            snapshotInv = minion.getInventory();
        } else if (!storageFull && storageInv != null) {
            snapshotInv = storageInv;
        } else {
            snapshotInv = minion.getInventory();
            useAutoSellTarget = true;
        }
        if (snapshotInv == null) {
            return false;
        }

        boolean producedAnything = false;
        HashMap<String, Integer> buffered = new HashMap<>();
        HashMap<String, Product> productMap = this.buildProductMap(minion, modifierMgr);

        producedAnything |= this.applyBaseProducts(minion, autoSellMod, storageInv, useAutoSellTarget, buffered);
        producedAnything |= this.applyUpgradeProducts(minion, autoSellMod, snapshotInv, storageInv,
                useAutoSellTarget, productMap, buffered);
        producedAnything |= this.flushBufferedProducts(minion, autoSellMod, storageInv, useAutoSellTarget,
                productMap, buffered);

        this.logInventorySnapshot(snapshotInv);
        return producedAnything;
    }

    /** Combines base products with any item-upgrade modifier products into a single map. */
    private HashMap<String, Product> buildProductMap(Minion minion, ModifierManager modifierMgr) {
        HashMap<String, Product> map = new HashMap<>();
        for (Product base : this.products) {
            map.put(base.getId(), base);
        }
        minion.getModifiers().stream()
                .map(d -> modifierMgr.get(d.getName()))
                .filter(Objects::nonNull)
                .filter(cfg -> cfg.type() == ModifierType.ITEM_UPGRADES)
                .flatMap(cfg -> cfg.upgradeProducts().stream())
                .forEach(p -> {
                    map.put(p.getId(), p);
                    DebugLogger.debug("[Work] Added upgrade product: " + p.getId());
                });
        return map;
    }

    /** Produces every base product (no required input). Anything that didn't fit is buffered. */
    /** Sum of LUCK modifier values on the minion (no external booster source — BoosterService has no LUCK kind). */
    private static double luckOf(Minion minion) {
        return ReMinions.getPlugin().getModifierManager()
                .getModifierNumber(minion, ModifierType.LUCK);
    }

    /** Final multiplier for output amount: {@code (1 + PRODUCTION_BOOST modifier) × external PRODUCTION booster}. */
    private static double productionMultiplierOf(Minion minion) {
        double mod = ReMinions.getPlugin().getModifierManager()
                .getModifierNumber(minion, ModifierType.PRODUCTION_BOOST);
        double ext = ReMinions.getPlugin().getBoosterService()
                .multiplier(minion.getOwner(), BoostKind.PRODUCTION);
        return (1.0 + mod) * ext;
    }

    private static int applyProductionBoost(int amount, Minion minion) {
        if (amount <= 0) return 0;
        return (int) Math.round(amount * productionMultiplierOf(minion));
    }

    private boolean applyBaseProducts(Minion minion, MinionModifierData autoSellMod,
                                      MinionInventory storageInv, boolean useAutoSellTarget,
                                      Map<String, Integer> buffered) {
        boolean produced = false;
        double luck = luckOf(minion);
        for (Product base : this.products) {
            if (base.getRequiredProduct() != null) continue;
            ItemStack item = base.buildItem();
            int amount = applyProductionBoost(base.getAmount(luck), minion);
            int inserted = this.tryInsert(minion, item, amount, minion.getInventory(), storageInv,
                    useAutoSellTarget, autoSellMod, base);
            if (inserted > 0) produced = true;
            if (inserted < amount) {
                buffered.merge(base.getId(), amount - inserted, Integer::sum);
                DebugLogger.debug("[Work] Buffered " + (amount - inserted) + " of " + base.getId());
            } else {
                DebugLogger.debug("[Work] Generated base product: " + base.getId());
            }
        }
        return produced;
    }

    /**
     * Iterates upgrade products (the ones with a required input). For each, consumes the
     * matching required items from inventory and produces the upgraded amount. The auto-sell
     * fast-path skips inventory mutation altogether and credits money directly.
     */
    private boolean applyUpgradeProducts(Minion minion, MinionModifierData autoSellMod,
                                         MinionInventory primaryInv, MinionInventory storageInv,
                                         boolean useAutoSellTarget,
                                         Map<String, Product> productMap,
                                         Map<String, Integer> buffered) {
        boolean produced = false;
        for (Product upgrade : new ArrayList<>(productMap.values())) {
            if (upgrade.getRequiredProduct() == null) continue;

            String requiredId = upgrade.getRequiredProduct();
            Product required = productMap.get(requiredId);
            if (required == null) {
                DebugLogger.debug("[Work] Required product not found: " + requiredId + " for upgrade " + upgrade.getId());
                continue;
            }
            ItemStack requiredItem = required.buildItem();
            int requiredAmount = upgrade.getRequiredAmount();
            int availableCount = primaryInv.countItem(requiredItem) + buffered.getOrDefault(requiredId, 0);
            if (availableCount < requiredAmount) {
                DebugLogger.debug("[Work] Missing required items for upgrade: " + upgrade.getId()
                        + " (" + requiredAmount + "x " + requiredItem.getType() + ")");
                continue;
            }

            int multiplier = availableCount / requiredAmount;
            int consumeTotal = multiplier * requiredAmount;

            if (!useAutoSellTarget) {
                produced |= this.consumeAndProduceUpgrade(minion, autoSellMod, primaryInv, storageInv,
                        upgrade, required, requiredItem, multiplier, consumeTotal, requiredId, buffered);
            } else {
                produced |= this.autoSellUpgrade(minion, autoSellMod, upgrade, multiplier);
            }
        }
        // Bonus-drop path: upgrade_products with no required_product — Product.getAmount(luck) handles the chance roll internally.
        double luck = luckOf(minion);
        for (Product upgrade : new ArrayList<>(productMap.values())) {
            if (upgrade.getRequiredProduct() != null) continue;
            if (upgrade.getChance() <= 0.0) continue;
            ItemStack item = upgrade.buildItem();
            int amount = applyProductionBoost(upgrade.getAmount(luck), minion);
            int inserted = this.tryInsert(minion, item, amount, minion.getInventory(), storageInv,
                    useAutoSellTarget, autoSellMod, upgrade);
            if (inserted > 0) produced = true;
        }
        return produced;
    }

    private boolean consumeAndProduceUpgrade(Minion minion, MinionModifierData autoSellMod,
                                             MinionInventory primaryInv, MinionInventory storageInv,
                                             Product upgrade, Product required, ItemStack requiredItem,
                                             int multiplier, int consumeTotal,
                                             String requiredId, Map<String, Integer> buffered) {
        boolean produced = false;
        int physicallyConsumed = Math.min(primaryInv.countItem(requiredItem), consumeTotal);
        if (physicallyConsumed > 0) {
            primaryInv.removeItem(MinionInventory.ItemData.of(requiredItem, physicallyConsumed));
            produced = true;
        }
        int needFromBuffer = consumeTotal - physicallyConsumed;
        if (needFromBuffer > 0) {
            buffered.put(requiredId, buffered.getOrDefault(requiredId, 0) - needFromBuffer);
        }

        int outputTotal = applyProductionBoost(multiplier * upgrade.getAmount(), minion);
        int inserted = this.tryInsert(minion, upgrade.buildItem(), outputTotal,
                minion.getInventory(), storageInv, false, autoSellMod, upgrade);
        if (inserted > 0) produced = true;
        if (inserted < outputTotal) {
            buffered.merge(upgrade.getId(), outputTotal - inserted, Integer::sum);
            DebugLogger.debug("[Work] Buffered upgraded product: " + upgrade.getId() + " x" + (outputTotal - inserted));
        }
        DebugLogger.debug("[Work] Upgraded product: " + upgrade.getId() + " -> Produced "
                + outputTotal + " (Consumed " + consumeTotal + "x " + requiredItem.getType() + ")");
        return produced;
    }

    private boolean autoSellUpgrade(Minion minion, MinionModifierData autoSellMod,
                                    Product upgrade, int multiplier) {
        int amount = applyProductionBoost(multiplier * upgrade.getAmount(), minion);
        if (autoSellMod != null) {
            double sellBoost = ReMinions.getPlugin().getBoosterService()
                    .multiplier(minion.getOwner(), BoostKind.SELL_PRICE);
            double modBoost = AutoSellPricing.effectiveMultiplier(autoSellMod);
            double revenue = ReMinions.getPlugin().getWorthService()
                    .resolveTotal(upgrade, upgrade.buildItem(), amount) * sellBoost * modBoost;
            MinionSellItemsEvent event = new MinionSellItemsEvent(minion, upgrade, revenue, amount);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                autoSellMod.addSoldItems(amount);
                autoSellMod.addMoneyEarned(revenue);
            }
        }
        DebugLogger.debug("[Work] Auto-sold upgrade product: " + upgrade.getId() + " x" + amount);
        return true;
    }


    /** Final pass: tries to ship any leftover buffered amounts (e.g. produced before inventory had room). */
    private boolean flushBufferedProducts(Minion minion, MinionModifierData autoSellMod,
                                          MinionInventory storageInv, boolean useAutoSellTarget,
                                          Map<String, Product> productMap, Map<String, Integer> buffered) {
        boolean produced = false;
        for (Entry<String, Integer> entry : buffered.entrySet()) {
            Product product = productMap.get(entry.getKey());
            int amount = entry.getValue();
            if (amount <= 0 || product == null) continue;
            int inserted = this.tryInsert(minion, product.buildItem(), amount, minion.getInventory(),
                    storageInv, useAutoSellTarget, autoSellMod, product);
            if (inserted > 0) produced = true;
        }
        return produced;
    }

    private void logInventorySnapshot(MinionInventory inventory) {
        DebugLogger.debug("Minion Inventory State:");
        for (MinionInventory.ItemData entry : inventory.getSnapshot()) {
            DebugLogger.debug(" - " + entry.getItem().getType() + " x" + entry.getAmount()
                    + " (Slots: " + entry.splitIntoStacks(entry.getItem().getMaxStackSize()).size() + ")");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Offline catch-up work
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Batches production for {@code elapsedMillis} of offline time. Uses the deterministic
     * production formula (no RNG) and applies upgrades iteratively so cascading upgrade
     * products can chain (up to {@link #MAX_UPGRADE_CASCADE_ITERATIONS}).
     */
    public boolean workOffline(Minion minion, ModifierManager modifierMgr, long elapsedMillis) {
        if (elapsedMillis <= 0L) {
            return false;
        }
        MinionInventory primaryInv = minion.getInventory();
        MinionInventory storageInv = minion.getStorage() != null ? minion.getStorage().inventory() : null;
        MinionModifierData autoSellMod = minion.getModifiersByAnyType(ModifierType.AUTO_SELL);

        Map<String, Product> allProducts = this.collectAllProducts(minion, modifierMgr);
        LinkedHashMap<String, Integer> baseAmounts = new LinkedHashMap<>();
        for (Product product : this.products) {
            if (product.getRequiredProduct() != null) continue;
            int amount = this.calculateDeterministicAmount(minion, product, elapsedMillis);
            if (amount > 0) {
                this.mergeAmount(baseAmounts, product.getId(), amount);
            }
        }

        Map<String, Integer> finalAmounts = this.applyOfflineUpgrades(minion, primaryInv, storageInv,
                allProducts, baseAmounts);

        boolean producedAnything = false;
        for (Entry<String, Integer> entry : finalAmounts.entrySet()) {
            Product product = allProducts.get(entry.getKey());
            int amount = entry.getValue();
            if (product == null || amount <= 0) continue;
            int inserted = this.tryInsert(minion, product.buildItem(), amount, primaryInv, storageInv,
                    false, autoSellMod, product);
            if (inserted > 0) producedAnything = true;
        }
        return producedAnything;
    }

    private Map<String, Product> collectAllProducts(Minion minion, ModifierManager modifierMgr) {
        LinkedHashMap<String, Product> map = new LinkedHashMap<>();
        for (Product base : this.products) {
            map.put(base.getId(), base);
        }
        minion.getModifiers().stream()
                .map(d -> modifierMgr.get(d.getName()))
                .filter(Objects::nonNull)
                .filter(cfg -> cfg.type() == ModifierType.ITEM_UPGRADES)
                .flatMap(cfg -> cfg.upgradeProducts().stream())
                .forEach(p -> map.put(p.getId(), p));
        return map;
    }

    /**
     * Computes the deterministic per-product amount for {@code elapsedMillis} of catch-up,
     * folding in the previous fractional remainder so the rate stays exact across runs.
     */
    private int calculateDeterministicAmount(Minion minion, Product product, long elapsedMillis) {
        // LUCK shifts the effective chance up; PRODUCTION_BOOST and the external Booster
        // service multiply the final per-tick output. Applied here so offline catch-up
        // matches online output exactly.
        double luckBoost = 1.0 + Math.max(0.0, luckOf(minion));
        double prodMult = productionMultiplierOf(minion);
        double expectedRate = product.getExpectedAmount() * luckBoost * prodMult;
        double total = minion.getProductionRemainder(product.getId())
                + expectedRate * elapsedMillis;
        if (total <= 0.0) {
            minion.setProductionRemainder(product.getId(), 0.0);
            return 0;
        }
        int produced = total >= 2.147483647E9 ? Integer.MAX_VALUE : (int) Math.floor(total);
        minion.setProductionRemainder(product.getId(), total - produced);
        return produced;
    }

    /**
     * Repeats the upgrade-cascade pass against a working copy of the running totals so that
     * a chain like {@code A -> B -> C} all resolves within a single offline catch-up run.
     */
    private Map<String, Integer> applyOfflineUpgrades(Minion minion, MinionInventory primaryInv,
                                                      MinionInventory storageInv,
                                                      Map<String, Product> products,
                                                      Map<String, Integer> baseAmounts) {
        List<Product> upgradeProducts = products.values().stream()
                .filter(p -> p.getRequiredProduct() != null).toList();
        if (upgradeProducts.isEmpty()) {
            return baseAmounts;
        }

        HashSet<String> relevantIds = new HashSet<>();
        for (Product upgrade : upgradeProducts) {
            relevantIds.add(upgrade.getId());
            relevantIds.add(upgrade.getRequiredProduct());
        }

        LinkedHashMap<String, Integer> running = new LinkedHashMap<>(baseAmounts);
        for (String productId : relevantIds) {
            Product product = products.get(productId);
            if (product == null) continue;
            ItemStack item = product.buildItem();
            int harvested = 0;
            if (primaryInv != null) harvested += primaryInv.removeSimilar(item, Integer.MAX_VALUE);
            if (storageInv != null) harvested += storageInv.removeSimilar(item, Integer.MAX_VALUE - harvested);
            if (harvested > 0) {
                this.mergeAmount(running, productId, harvested);
            }
        }

        int iteration = 0;
        boolean stillProcessing;
        do {
            stillProcessing = false;
            iteration++;
            for (Product upgrade : upgradeProducts) {
                String requiredId = upgrade.getRequiredProduct();
                int requiredAmount = Math.max(1, upgrade.getRequiredAmount());
                int available = running.getOrDefault(requiredId, 0);
                if (available < requiredAmount) continue;
                int multiplier = available / requiredAmount;
                int perProduce = Math.max(1, upgrade.getDeterministicAmount());
                running.put(requiredId, available - multiplier * requiredAmount);
                this.mergeAmount(running, upgrade.getId(), (long) multiplier * perProduce);
                stillProcessing = true;
            }
        } while (stillProcessing && iteration < MAX_UPGRADE_CASCADE_ITERATIONS);

        running.entrySet().removeIf(e -> e.getValue() <= 0);
        return running;
    }

    /** Adds {@code amount} to {@code map[key]} with int overflow clamped at {@link Integer#MAX_VALUE}. */
    private void mergeAmount(Map<String, Integer> map, String key, long amount) {
        if (key == null || amount <= 0L) return;
        int clamped = amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        map.merge(key, clamped, (a, b) -> {
            long sum = (long) a + b;
            return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Inventory insertion with auto-sell fallback
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Tries to insert {@code totalAmount} of {@code item} into the primary inventory, falling
     * back to {@code storageInv} for any leftover, and finally — if {@code autoSellMod} is
     * non-null — sells the remainder for the configured price.
     *
     * @return total amount that ended up landing somewhere (primary + storage + sold)
     */
    private int tryInsert(Minion minion, ItemStack item, int totalAmount,
                          MinionInventory primaryInv, MinionInventory storageInv,
                          boolean autoSellOnly, MinionModifierData autoSellMod, Product product) {
        int placed = 0;
        if (primaryInv != null) {
            List<MinionInventory.ItemData> accepted = primaryInv.addItems(MinionInventory.ItemData.of(item, totalAmount));
            placed += accepted.stream().mapToInt(MinionInventory.ItemData::getAmount).sum();
            MinionItemsProduceEvent.ResultState state = !accepted.isEmpty()
                    ? MinionItemsProduceEvent.ResultState.SUCESS
                    : MinionItemsProduceEvent.ResultState.FAILED;
            Bukkit.getPluginManager().callEvent(new MinionItemsProduceEvent(
                    minion, primaryInv, accepted.toArray(new MinionInventory.ItemData[0]), state));
        }

        int remaining = totalAmount - placed;
        if (remaining > 0 && storageInv != null) {
            List<MinionInventory.ItemData> accepted = storageInv.addItems(MinionInventory.ItemData.of(item, remaining));
            placed += accepted.stream().mapToInt(MinionInventory.ItemData::getAmount).sum();
            MinionItemsProduceEvent.ResultState state = !accepted.isEmpty()
                    ? MinionItemsProduceEvent.ResultState.SUCESS
                    : MinionItemsProduceEvent.ResultState.FAILED;
            Bukkit.getPluginManager().callEvent(new MinionItemsProduceEvent(
                    minion, primaryInv, accepted.toArray(new MinionInventory.ItemData[0]), state));
        }

        remaining = totalAmount - placed;
        if (remaining > 0 && autoSellMod != null && product != null) {
            double sellBoost = ReMinions.getPlugin().getBoosterService()
                    .multiplier(minion.getOwner(), BoostKind.SELL_PRICE);
            double modBoost = AutoSellPricing.effectiveMultiplier(autoSellMod);
            double revenue = ReMinions.getPlugin().getWorthService()
                    .resolveTotal(product, item, remaining) * sellBoost * modBoost;
            MinionSellItemsEvent event = new MinionSellItemsEvent(minion, product, revenue, remaining);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                autoSellMod.addSoldItems(remaining);
                autoSellMod.addMoneyEarned(revenue);
                DebugLogger.debug("[Work] Auto-sold " + remaining + " of " + product.getId() + " (no space)");
                placed += remaining;
            }
        }

        int skipped = totalAmount - placed;
        if (skipped > 0) {
            DebugLogger.debug("[Work] Skipped " + skipped + " of "
                    + (product != null ? product.getId() : item.getType()) + " (no space in main+storage)");
        }
        return placed;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Upgrade path (consume required items from the player's inventory and level up)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Attempts to upgrade {@code minion} by one level using the items in {@code player}'s inventory.
     * Returns {@code false} when the player lacks the required items.
     */
    public boolean upgradeMinion(Minion minion, ReMinions plugin, PlayerMinions playerMinions, Player player) {
        int currentLevel = minion.getLevel();
        MinionUpgrade nextUpgrade = this.getUpgrade(currentLevel + 1);
        if (nextUpgrade == null) {
            return false;
        }

        MinionUpgrade.Requirement requirement = nextUpgrade.requirement();
        Map<Character, MinionUpgrade.ItemRequirement> requirementBySymbol = requirement.items();
        HashMap<Character, Integer> requiredCountBySymbol = new HashMap<>();
        for (char symbol : requirement.shape()) {
            if (symbol != ' ' && requirementBySymbol.containsKey(symbol)) {
                requiredCountBySymbol.merge(symbol, requirementBySymbol.get(symbol).amount(), Integer::sum);
            }
        }

        List<Product> requirementProducts = requirement.items().values().stream()
                .map(MinionUpgrade.ItemRequirement::product).toList();

        Map<String, Integer> availableByProductId = Arrays.stream(player.getInventory().getContents())
                .filter(Objects::nonNull)
                .<Map.Entry<String, Integer>>flatMap(item -> requirementProducts.stream()
                        .filter(p -> p.matches(item))
                        .map(p -> Map.entry(p.getId(), item.getAmount())))
                .collect(HashMap::new,
                        (m, e) -> m.merge(e.getKey(), e.getValue(), Integer::sum),
                        HashMap::putAll);

        boolean hasEnough = requiredCountBySymbol.entrySet().stream().allMatch(e -> {
            MinionUpgrade.ItemRequirement req = requirementBySymbol.get(e.getKey());
            return availableByProductId.getOrDefault(req.product().getId(), 0) >= e.getValue();
        });
        if (!hasEnough) {
            plugin.getConfig0().sendMessage(player, "upgrade_missing_items");
            return false;
        }

        // Consume the required items.
        for (Entry<Character, Integer> entry : requiredCountBySymbol.entrySet()) {
            MinionUpgrade.ItemRequirement req = requirementBySymbol.get(entry.getKey());
            Product product = req.product();
            int remaining = entry.getValue();
            for (ItemStack stack : player.getInventory().getContents()) {
                if (remaining <= 0) break;
                if (stack == null || !product.matches(stack)) continue;
                int stackAmount = stack.getAmount();
                if (stackAmount > remaining) {
                    stack.setAmount(stackAmount - remaining);
                    remaining = 0;
                    break;
                }
                remaining -= stackAmount;
                stack.setAmount(0);
            }
            if (remaining > 0) {
                DebugLogger.debug("Could not remove all required items from" + product.getId());
            }
        }

        // Apply upgrade + visual refresh.
        minion.addLevel(1);
        minion.setCacheProductionSpeed(nextUpgrade.productionSpeed());
        minion.getInventory().setMaxSlots(nextUpgrade.maxStorage());

        Config config = plugin.getConfig0();
        String nextSkinKey = Optional.ofNullable(this.getSkinLevel(currentLevel + 1))
                .orElse(this.getSkinLevel(currentLevel));
        if (nextSkinKey != null) {
            MinionSkinConfig nextSkin = plugin.getSkinManager().get(nextSkinKey);
            if (nextSkin != null) {
                minion.setSkinLevel(nextSkinKey);
                minion.despawn();
                minion.spawn(nextSkin);
            }
        }

        this.updateUniqueMinions(playerMinions, config, player, minion.getLevel());
        config.sendMessage(player, "upgrade_success",
                "%minion_name%", this.name.replace("%roman_level%", String.valueOf(currentLevel)),
                "%roman_level%", currentLevel + 1);
        return true;
    }

    /**
     * Tracks "unique minions unlocked" milestones across the player and unlocks the next reward
     * tier when a new high-water mark is reached. Sends a contextual chat message either way.
     */
    public void updateUniqueMinions(PlayerMinions playerMinions, Config config, Player player, int newLevel) {
        int currentMaxMinions = playerMinions.getMaxMinions();
        int previousLevel = playerMinions.getMinionUnlockeds().getOrDefault(this.id, 0);

        if (newLevel > previousLevel) {
            playerMinions.getMinionUnlockeds().put(this.id, newLevel);
            int newTotal = playerMinions.getTotalUniqueMinions();
            MinionRewardConfig reward = config.getMinionReward(newTotal);
            if (reward != null) {
                playerMinions.setMaxMinions(reward.newMaxMinions());
                if (player != null) {
                    config.sendMessage(player, "minions_unique_unlocked",
                            "%new_max_minions%", reward.newMaxMinions());
                }
                return;
            }
            if (player != null) {
                this.sendNextRewardMessage(config, player, newTotal, currentMaxMinions);
            }
        } else if (player != null) {
            int total = playerMinions.getTotalUniqueMinions();
            this.sendNextRewardMessage(config, player, total, currentMaxMinions);
        }
    }

    private void sendNextRewardMessage(Config config, Player player, int currentTotal, int currentMaxMinions) {
        MinionRewardConfig nextReward = config.getNextMinionReward(currentTotal);
        if (nextReward == null) {
            config.sendMessage(player, "minions_max_unique_reached");
            return;
        }
        config.sendMessage(player, "minions_unique_required_next",
                "%current_minions%", currentTotal,
                "%max_minions%", currentMaxMinions,
                "%required_minions_next_reward%",
                Math.max(0, nextReward.requiredUniqueMinions() - currentTotal));
    }
}
