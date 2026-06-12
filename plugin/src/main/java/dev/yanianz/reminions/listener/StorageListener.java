package dev.yanianz.reminions.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.Keys;
import dev.yanianz.reminions.config.StorageConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.utils.InventoryTransfer;
import dev.yanianz.reminions.utils.Location3f;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages the "storage" block that can be attached next to a placed minion:
 *
 * <ul>
 *   <li>Right-click while holding a storage item: turn the clicked block's top face into a
 *       storage container, binding it to the closest adjacent minion that doesn't yet have one.</li>
 *   <li>Break a storage block: return the items + the storage item to the owner, clear the link.</li>
 *   <li>Right-click an existing storage block: open the storage menu for the linked minion.</li>
 * </ul>
 */
public class StorageListener implements Listener {

    /** Half-width of the box used to find a minion armorstand adjacent to a candidate storage block. */
    private static final double NEARBY_HALF_WIDTH_BLOCKS = 1.5;

    /** Half-height of the same box (allows the block to be 1 below or above the armorstand foot). */
    private static final double NEARBY_HALF_HEIGHT_BLOCKS = 2.0;

    private final ReMinions plugin;

    public StorageListener(ReMinions plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Place: right-click a block while holding a storage item
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        PlayerMinions playerMinions = this.plugin.getPlayerManager().getById(player.getUniqueId());
        if (playerMinions == null) return;

        ItemStack handItem = event.getItem();
        Block clickedBlock = event.getClickedBlock();
        if (handItem == null || clickedBlock == null) return;

        ItemKey storageKey = ItemBuilder.getPersistentKey(handItem, "storage_item");
        if (storageKey == null) return;

        StorageConfig storageConfig = this.plugin.getStorageManager().get(storageKey.value());
        if (storageConfig == null) return;

        event.setCancelled(true);
        Block storageBlock = clickedBlock.getRelative(BlockFace.UP);

        this.findAdjacentMinionWithoutStorage(storageBlock, playerMinions)
                .ifPresentOrElse(
                        minion -> this.attachStorage(minion, storageBlock, handItem, storageConfig, player),
                        () -> this.plugin.getConfig0().sendMessage(player, "storage_no_minion_near"));
    }

    /** Finds the first armorstand-bound minion that is directly N/S/E/W of {@code storageBlock} at the same Y. */
    private java.util.Optional<Minion> findAdjacentMinionWithoutStorage(Block storageBlock, PlayerMinions playerMinions) {
        return storageBlock.getWorld().getNearbyEntities(
                        storageBlock.getLocation(),
                        NEARBY_HALF_WIDTH_BLOCKS,
                        NEARBY_HALF_HEIGHT_BLOCKS,
                        NEARBY_HALF_WIDTH_BLOCKS,
                        e -> e instanceof ArmorStand
                                && e.getPersistentDataContainer().has(Keys.MINION_ARMORSTAND, PersistentDataType.STRING))
                .stream()
                .map(e -> (ArmorStand) e)
                .map(stand -> stand.getPersistentDataContainer().get(Keys.MINION_ARMORSTAND, PersistentDataType.STRING))
                .filter(Objects::nonNull)
                .map(UUID::fromString)
                .map(playerMinions::getMinionById)
                .filter(m -> m != null && m.getStorage() == null)
                .filter(m -> this.isCardinallyAdjacent(m.getLoc().toLocation(), storageBlock.getLocation()))
                .findFirst();
    }

    private boolean isCardinallyAdjacent(Location minionLoc, Location storageLoc) {
        int mx = minionLoc.getBlockX();
        int my = minionLoc.getBlockY();
        int mz = minionLoc.getBlockZ();
        int sx = storageLoc.getBlockX();
        int sy = storageLoc.getBlockY();
        int sz = storageLoc.getBlockZ();
        if (my != sy) return false;
        return (sx == mx + 1 && sz == mz)
                || (sx == mx - 1 && sz == mz)
                || (sx == mx && sz == mz + 1)
                || (sx == mx && sz == mz - 1);
    }

    private void attachStorage(Minion minion, Block storageBlock, ItemStack handItem,
                               StorageConfig storageConfig, Player player) {
        // Orient the storage block to face away from the player (matches vanilla place feel).
        if (storageBlock.getBlockData() instanceof Directional directional) {
            directional.setFacing(player.getFacing().getOppositeFace());
            storageBlock.setBlockData(directional);
        }

        MinionInventory inv = new MinionInventory(new ArrayList<>(), 0L, storageConfig.maxStorage());
        minion.setStorage(new MinionStorage(storageConfig.id(), player.getUniqueId(), inv,
                new Location3f(storageBlock.getLocation())));
        storageBlock.setType(storageConfig.blockSkin());
        handItem.setAmount(handItem.getAmount() - 1);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Break: return items + storage item to the owner
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        PlayerMinions playerMinions = this.plugin.getPlayerManager().getById(player.getUniqueId());
        if (playerMinions == null) return;

        int bx = block.getX(), by = block.getY(), bz = block.getZ();
        playerMinions.getMinions().stream()
                .filter(m -> m.getStorage() != null)
                .filter(m -> {
                    Location storageLoc = m.getStorage().location().toLocation();
                    return storageLoc.getBlockX() == bx && storageLoc.getBlockY() == by && storageLoc.getBlockZ() == bz;
                })
                .findFirst()
                .ifPresent(minion -> this.handleStorageBreak(minion, block, event, player));
    }

    private void handleStorageBreak(Minion minion, Block block, BlockBreakEvent event, Player player) {
        if (!minion.getStorage().owner().equals(player.getUniqueId())) {
            this.plugin.getConfig0().sendMessage(player, "storage_not_owner");
            event.setCancelled(true);
            return;
        }
        MinionStorage storage = minion.getStorage();
        StorageConfig storageConfig = this.plugin.getStorageManager().get(storage.name());
        if (storageConfig == null) {
            event.setCancelled(true);
            return;
        }
        ItemStack storageItem = storageConfig.item().toBuild(
                "%storage%", storageConfig.maxStorage(),
                "%block_skin%", storageConfig.blockSkin().name());
        List<ItemStack> dropPayload = InventoryTransfer.flattenWithExtra(storage.inventory(), storageItem);
        if (!InventoryTransfer.canFit(player.getInventory(), dropPayload)
                || !InventoryTransfer.addAll(player.getInventory(), dropPayload)) {
            this.plugin.getConfig0().sendMessage(player, "storage_break_inventory_full");
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        block.setType(Material.AIR);
        minion.setStorage(null);
        this.plugin.getConfig0().sendMessage(player, "storage_removed");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Open: right-click an existing storage block to open the storage menu
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        PlayerMinions playerMinions = this.plugin.getPlayerManager().getById(player.getUniqueId());
        if (playerMinions == null) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Minion minion = this.plugin.getPlayerManager().getMinionByStorage(block.getLocation(), player.getUniqueId());
        if (minion == null) return;

        if (!minion.getOwner().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        StorageConfig storageConfig = this.plugin.getStorageManager().get(minion.getStorage().name());
        if (storageConfig == null) return;

        event.setCancelled(true);
        playerMinions.setViewMinion(minion.getId());
        this.plugin.getMenuManager().openMenu("storage_menu", 0, storageConfig.rows(), false, player,
                "%storage_name%", storageConfig.name());
    }
}
