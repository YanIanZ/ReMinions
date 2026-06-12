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
 * Pure Bukkit-API bridge implementations used when no version-specific NMS module is
 * available for the running server. Keeps the plugin functional on unsupported
 * versions — only the recipe-book ghost packet feature degrades (no-op).
 */
public final class FallbackBridges {

    public static final InventoryBridge INVENTORY = new InventoryBridge() {
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

    public static final ItemBridge ITEM = new ItemBridge() {
        @Override
        public Enchantment applyEnchant(String enchantName) {
            if (enchantName == null) return null;
            NamespacedKey key = NamespacedKey.minecraft(enchantName.toLowerCase());
            return Registry.ENCHANTMENT.get(key);
        }
    };

    public static final ParticleBridge PARTICLE = new ParticleBridge() {
        @Override public Particle cropGrowParticle()  { return Particle.HAPPY_VILLAGER; }
        @Override public Particle magicParticle()     { return Particle.WITCH; }
        @Override public Particle explosionParticle() { return Particle.EXPLOSION; }
        @Override public Particle splashParcicle()    { return Particle.SPLASH; }
        @Override public Particle dustParticle()      { return Particle.DUST; }
        @Override public Particle entityParticle()    { return Particle.ANGRY_VILLAGER; }
        @Override public Particle bubbleParticle()    { return Particle.BUBBLE; }
    };

    public static final NMSHandler NMS_HANDLER = new NMSHandler() {
        @Override
        public void sendGhostRecipe(com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent event,
                                    dev.yanianz.reminions.config.Config config,
                                    dev.yanianz.reminions.managers.MinionManager minionManager,
                                    org.bukkit.inventory.Recipe recipe,
                                    org.bukkit.inventory.ItemStack resultItem) {
            // No-op — ghost recipe needs NMS access we do not have on this server version.
        }

        @Override
        public void updateInventoryTitle(org.bukkit.inventory.InventoryView inventoryView, String newTitle) {
            // No reflection fallback; close + open would reset cursor. Leave as no-op.
        }
    };

    private FallbackBridges() {}
}
