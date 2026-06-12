package dev.yanianz.reminions.listener;

import java.util.ArrayList;
import java.util.UUID;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.api.events.MinionPlaceEvent;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionMeta;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.managers.RecipeManager;
import dev.yanianz.reminions.placeholder.PlaceholderReplacer;
import dev.yanianz.reminions.utils.Location3f;
import dev.yanianz.reminions.utils.Text;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Player-centric event handlers:
 *
 * <ul>
 *   <li>Join: load PlayerMinions from DB / initialise a new one + discover recipes.</li>
 *   <li>Quit: despawn & async-persist owned minions.</li>
 *   <li>Block place: turn a minion-item into a placed {@link Minion} (with proximity + per-world checks).</li>
 *   <li>Right-click / left-click an armorstand minion → open the minion menu.</li>
 *   <li>Right-click with a modifier / skin item: prevent the vanilla interaction.</li>
 * </ul>
 */
public class PlayerListener implements Listener {

    /** Minimum search radius (in blocks) for the place-time proximity scan. */
    private static final double MIN_PROXIMITY_SEARCH_BLOCKS = 8.0;

    /** Extra slack added to the spatial query so a neighbour's radius is fully covered. */
    private static final int PROXIMITY_SEARCH_SLACK_BLOCKS = 16;

    /** Y separation considered "stacked" — armorstand is ~2 blocks tall. */
    private static final double VERTICAL_STACK_THRESHOLD = 2.0;

    private static final int DEFAULT_MAX_MINIONS_FALLBACK = 5;
    private static final String LUCKPERMS_LIMIT_PREFIX = "beeminions.place.limit.";

    private final ReMinions plugin;

    public PlayerListener(ReMinions plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Join / Quit
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerMinions data = this.plugin.getDatabase().getPlayerMinions(playerId);
        if (data == null) {
            data = new PlayerMinions(playerId, player.getName());
            data.setMaxMinions(this.plugin.getConfig0().getInt("settings.default_max_minions", DEFAULT_MAX_MINIONS_FALLBACK));
        }

        player.discoverRecipes(RecipeManager.getRecipes());
        this.plugin.getPlayerManager().add(playerId, data);

        // If the server config forbids offline production for this player, reset their action
        // anchor so they don't get a giant catch-up burst on the first tick after rejoining.
        if (!this.canUseOfflineProduction(player)) {
            long nowMs = System.currentTimeMillis();
            for (Minion minion : data.getMinions()) {
                minion.setLastGenerated(nowMs);
            }
        }
    }

    private boolean canUseOfflineProduction(Player player) {
        Config config = this.plugin.getConfig0();
        if (!config.getBoolean("settings.minions_offline_mode")) {
            return false;
        }
        if (!config.getBoolean("settings.required_permission_offline_mode")) {
            return true;
        }
        return player.hasPermission("beeminions.offlinemode.produce");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerMinions data = this.plugin.getPlayerManager().remove(playerId);
        if (data == null) {
            return;
        }
        data.getMinions().forEach(Minion::despawn);
        // Move the DB write off the main thread so several simultaneous quits do not stall the server tick.
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin,
                () -> this.plugin.getDatabase().savePlayerMinions(data));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Minion placement (player right-clicks block while holding a minion item)
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onInteract(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerMinions playerMinions = this.plugin.getPlayerManager().getById(player.getUniqueId());
        if (playerMinions == null) {
            return;
        }
        Block placedBlock = event.getBlockPlaced();
        ItemStack handItem = event.getItemInHand();
        if (placedBlock == null || handItem == null) {
            return;
        }
        ItemKey minionItemKey = ItemBuilder.getPersistentKey(handItem, "minion_item");
        if (minionItemKey == null) {
            return;
        }

        event.setCancelled(true);

        MinionMeta meta = ItemBuilder.GSON.fromJson(minionItemKey.value(), MinionMeta.class);
        MinionConfig config = this.plugin.getMinionManager().get(meta.getId());
        if (config == null) {
            return;
        }

        int level = meta.getLevel();
        MinionUpgrade upgrade = config.getUpgrade(level);
        if (upgrade == null) {
            return;
        }

        int placementLimit = this.getMaxLimit(player, playerMinions);
        if (playerMinions.getCurrentMinions() >= placementLimit) {
            this.plugin.getConfig0().sendMessage(player, "minion_place_limit_reached", "%limit%", placementLimit);
            return;
        }

        if (this.isWorldRestricted(player)) {
            this.plugin.getConfig0().sendMessage(player, "world_not_place_minion");
            return;
        }

        String skinLevelKey = config.getSkinLevel(level);
        if (skinLevelKey == null) {
            this.plugin.getConfig0().sendMessage(player, "invalid_skin_level_minion",
                    "%level%", level, "%minion_id%", config.id());
            return;
        }

        Location placeLoc = placedBlock.getLocation().add(0.5, 0.0, 0.5);
        int baseRadius = config.baseRadius();

        if (this.tooCloseToAnotherMinion(placeLoc, baseRadius, player)) {
            return;
        }

        placeLoc.setYaw(this.facingYaw(player));
        MinionPlaceEvent placeEvent = new MinionPlaceEvent(player, placeLoc, meta);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            return;
        }

        this.spawnMinion(player, playerMinions, config, upgrade, meta, level, skinLevelKey, placeLoc, handItem);
    }

    private boolean isWorldRestricted(Player player) {
        Config config = this.plugin.getConfig0();
        return config.getBoolean("settings.world_whitelist.enabled")
                && !config.getStringList("settings.world_whitelist.worlds")
                          .contains(player.getLocation().getWorld().getName());
    }

    /**
     * Returns {@code true} when an existing minion sits within the combined horizontal radius
     * and is not vertically stacked far enough to be considered a separate floor.
     */
    private boolean tooCloseToAnotherMinion(Location placeLoc, int baseRadius, Player player) {
        // Spatial index query: O(neighbour-buckets) instead of O(loaded-entities-in-50-blocks).
        double searchR = Math.max(MIN_PROXIMITY_SEARCH_BLOCKS, baseRadius + PROXIMITY_SEARCH_SLACK_BLOCKS);
        for (UUID otherId : this.plugin.getPlayerManager().spatialIndex().queryNearby(placeLoc, searchR)) {
            Minion other = this.plugin.getPlayerManager().getMinionById(otherId);
            if (other == null || other.getLoc() == null) continue;
            if (!other.getLoc().toLocation().getWorld().equals(placeLoc.getWorld())) continue;

            int required = other.getBaseRadius() + baseRadius;
            double dx = Math.abs(placeLoc.getX() - other.getLoc().getX());
            double dz = Math.abs(placeLoc.getZ() - other.getLoc().getZ());
            double dy = Math.abs(placeLoc.getY() - other.getLoc().getY());
            // Allow vertical stacking provided at least 1 empty block above (armorstand ~2 blocks tall).
            if (dx <= required && dz <= required && dy < VERTICAL_STACK_THRESHOLD) {
                this.plugin.getConfig0().sendMessage(player, "minion_too_close", "%required_distance%", required);
                return true;
            }
        }
        return false;
    }

    /** Snaps the armorstand yaw to the player's facing cardinal direction. */
    private float facingYaw(Player player) {
        BlockFace facing = player.getFacing();
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST  -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST  -> -90.0F;
            default    -> player.getLocation().getYaw();
        };
    }

    /** Builds + registers the new minion runtime object and updates the player's UI. */
    private void spawnMinion(Player player, PlayerMinions playerMinions, MinionConfig config,
                             MinionUpgrade upgrade, MinionMeta meta, int level, String skinLevelKey,
                             Location placeLoc, ItemStack handItem) {
        MinionInventory inv = new MinionInventory(new ArrayList<>(), meta.getCollected());
        Minion minion = new Minion(UUID.randomUUID(), meta.getId(), player.getUniqueId(),
                config.type(), inv, config, new Location3f(placeLoc));
        inv.setMaxSlots(upgrade.maxStorage());
        minion.setLevel(level);
        minion.setSkinLevel(skinLevelKey);
        minion.setCacheProductionSpeed(config.getProductionSpeed(level));
        minion.setLastGenerated(System.currentTimeMillis());

        handItem.setAmount(handItem.getAmount() - 1);
        player.getInventory().setItemInMainHand(handItem);
        this.plugin.getPlayerManager().addMinion(playerMinions, minion);

        Config config0 = this.plugin.getConfig0();
        config0.sendMessage(player, "minion_placed",
                "%minion_name%", PlaceholderReplacer.replaceInlineText(config.name(), "%roman_level%", Text.toRomanLevel(level)),
                "%current_minions%", playerMinions.getCurrentMinions(),
                "%max_minions%", playerMinions.getMaxMinions());
        config.updateUniqueMinions(playerMinions, config0, player, level);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Per-player max minions (LuckPerms permission ladder takes precedence)
    // ─────────────────────────────────────────────────────────────────────────────

    private int getMaxLimit(Player player, PlayerMinions playerMinions) {
        if (!this.plugin.isLuckPermsInstalled()) {
            return playerMinions.getMaxMinions();
        }
        try {
            User user = this.plugin.getLuckPerms().getPlayerAdapter(Player.class).getUser(player);
            return user.getNodes(NodeType.PERMISSION).stream()
                    .map(PermissionNode.class::cast)
                    .<String>map(Node::getKey)
                    .filter(key -> key.startsWith(LUCKPERMS_LIMIT_PREFIX))
                    .map(key -> key.substring(LUCKPERMS_LIMIT_PREFIX.length()))
                    .mapToInt(suffix -> {
                        try {
                            return Integer.parseInt(suffix);
                        } catch (NumberFormatException ignored) {
                            return 0;
                        }
                    })
                    .max()
                    .orElse(playerMinions.getMaxMinions());
        } catch (IllegalStateException notReady) {
            this.plugin.getLogger().warning("LuckPerms not fully loaded when checking minion limits for " + player.getName());
            return playerMinions.getMaxMinions();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Interactions with a placed minion
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof ArmorStand stand) {
            this.tryOpenMinionMenu(event.getPlayer(), stand);
        }
    }

    @EventHandler
    public void onLeftClickMinion(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        String minionId = stand.getPersistentDataContainer()
                .get(dev.yanianz.reminions.Keys.MINION_ARMORSTAND, PersistentDataType.STRING);
        if (minionId == null) return;
        event.setCancelled(true);
        this.tryOpenMinionMenu(player, stand);
    }

    /** Opens the minion menu when {@code player} clicked their own placed minion armorstand. */
    private void tryOpenMinionMenu(Player player, ArmorStand stand) {
        String minionIdStr = stand.getPersistentDataContainer()
                .get(dev.yanianz.reminions.Keys.MINION_ARMORSTAND, PersistentDataType.STRING);
        if (minionIdStr == null) return;

        PlayerMinions playerMinions = this.plugin.getPlayerManager().getById(player.getUniqueId());
        if (playerMinions == null) return;

        Minion minion = playerMinions.getMinionById(UUID.fromString(minionIdStr));
        if (minion == null) return;

        MinionConfig config = this.plugin.getMinionManager().get(minion.getName());
        if (config == null) return;

        playerMinions.setViewMinion(minion.getId());
        this.plugin.getMenuManager().openMenu(
                "minion_menu", 0, 0, false, player,
                "%minion_name%", PlaceholderReplacer.replaceInlineText(config.name(),
                        "%roman_level%", Text.toRomanLevel(minion.getLevel()))
        );
    }

    /**
     * Blocks vanilla interactions when the player right-clicks a modifier / skin item against a
     * block (those should only ever be used through the menu drag-drop).
     */
    @EventHandler
    public void onInteractModifier(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || event.getClickedBlock() == null) {
            return;
        }
        boolean isModifierItem = ItemBuilder.getPersistentKey(item, "modifier_item") != null;
        boolean isSkinItem = ItemBuilder.getPersistentKey(item, "skin_item") != null;
        if (isModifierItem || isSkinItem) {
            event.setCancelled(true);
        }
    }
}
