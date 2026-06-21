package dev.yanianz.reminions.nms;

import dev.yanianz.reminions.menu.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Pure Bukkit/Paper-API implementation shared by every API-only bucket adapter (1.20.x and
 * 1.21.0–1.21.10). Provides every bridge surface without touching {@code net.minecraft.*}
 * internals; ghost-recipe and inventory-title updates degrade to no-ops on these versions.
 *
 * <p>Each version-bucket module ships thin concrete subclasses ({@code NMSHandlerImpl_v1_20_R1},
 * etc.) that exist purely so {@link NMSHandlerProvider} can report which bucket resolved at
 * boot. The behaviour is identical — particle enum names are looked up via
 * {@link ParticleResolver#resolve(String...)} so the 1.20.5 rename is handled transparently.</p>
 */
public abstract class ApiBackedNmsAdapter {

    public static final InventoryBridge INVENTORY_BRIDGE = new InventoryBridge() {
        @Override
        public InventoryHolder getTopHolder(Player player) {
            return player.getOpenInventory().getTopInventory().getHolder();
        }

        @Override
        public void closeInventoryPlayers() {
            Bukkit.getOnlinePlayers().stream().filter(p -> {
                Inventory inv = p.getOpenInventory().getTopInventory();
                return inv != null && inv.getHolder() instanceof MenuHolder;
            }).forEach(HumanEntity::closeInventory);
        }
    };

    public static final ItemBridge ITEM_BRIDGE = new ItemBridge() {
        @Override
        public Enchantment applyEnchant(String enchantName) {
            if (enchantName == null) return null;
            NamespacedKey key = NamespacedKey.minecraft(enchantName.toLowerCase());
            return Registry.ENCHANTMENT.get(key);
        }
    };

    public static final ParticleBridge PARTICLE_BRIDGE = new ParticleBridge() {
        @Override public Particle cropGrowParticle()  { return ParticleResolver.resolve("HAPPY_VILLAGER", "VILLAGER_HAPPY"); }
        @Override public Particle magicParticle()     { return ParticleResolver.resolve("WITCH", "SPELL_WITCH"); }
        @Override public Particle explosionParticle() { return ParticleResolver.resolve("EXPLOSION", "EXPLOSION_NORMAL"); }
        @Override public Particle splashParcicle()    { return ParticleResolver.resolve("SPLASH", "WATER_SPLASH"); }
        @Override public Particle dustParticle()      { return ParticleResolver.resolve("DUST", "REDSTONE"); }
        @Override public Particle entityParticle()    { return ParticleResolver.resolve("ANGRY_VILLAGER", "VILLAGER_ANGRY"); }
        @Override public Particle bubbleParticle()    { return ParticleResolver.resolve("BUBBLE", "WATER_BUBBLE"); }
    };

    public static final NMSHandler NMS_HANDLER = new NMSHandler() {
        @Override
        public void sendGhostRecipe(com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent event,
                                    dev.yanianz.reminions.config.Config config,
                                    dev.yanianz.reminions.managers.MinionManager minionManager,
                                    org.bukkit.inventory.Recipe recipe,
                                    org.bukkit.inventory.ItemStack resultItem) {
            // No-op on legacy buckets — the ghost recipe packet shape changes between minor
            // versions, and we don't ship a paperweight bundle here. Server still accepts the
            // craft, players just don't see the auto-fill preview.
        }

        @Override
        public void updateInventoryTitle(org.bukkit.inventory.InventoryView inventoryView, String newTitle) {
            // Paper 1.20+ exposes setTitle on InventoryView directly. Catch any older shape.
            try {
                inventoryView.setTitle(newTitle);
            } catch (UnsupportedOperationException | NoSuchMethodError ignored) {
                // not available on this server — leave title untouched
            }
        }
    };

    protected ApiBackedNmsAdapter() {}
}
