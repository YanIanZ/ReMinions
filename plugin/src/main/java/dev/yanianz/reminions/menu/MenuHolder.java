package dev.yanianz.reminions.menu;

import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MenuConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemMenu;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class MenuHolder implements InventoryHolder {

    protected final Inventory inventory;
    protected final PlayerMinions playerMinions;
    protected final MenuConfig menuConfig;
    protected final ReMinions plugin;

    protected MenuHolder(ReMinions plugin, PlayerMinions playerMinions, MenuConfig config, Object... placeholders) {
        this.inventory     = Bukkit.createInventory(this, config.rows() * 9, Text.parseComponent(config.title(), placeholders));
        this.playerMinions = playerMinions;
        this.menuConfig    = config;
        this.plugin        = plugin;
    }

    protected MenuHolder(ReMinions plugin, PlayerMinions playerMinions, MenuConfig config, int rows, Object... placeholders) {
        this.inventory     = Bukkit.createInventory(this, rows * 9, Text.parseComponent(config.title(), placeholders));
        this.playerMinions = playerMinions;
        this.menuConfig    = config;
        this.plugin        = plugin;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public abstract void onOpen();

    public abstract void onClick(Player player, ItemBuilder clickedItem, InventoryClickEvent event, int slot);

    public ItemBuilder getItemById(String itemId) {
        return this.menuConfig.items().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    protected void populateDecorations() {
        populateMenuItems(this.menuConfig.decorations());
    }

    protected void populateItems() {
        populateMenuItems(this.menuConfig.items());
    }

    private void populateMenuItems(Iterable<ItemMenu> menuItems) {
        for (ItemMenu menuItem : menuItems) {
            ItemStack stack = menuItem.toBuild(new Object[0]);
            int[] slots = menuItem.getSlots();
            if (slots.length > 0 && slots[0] == -1) {
                for (int i = 0; i < this.inventory.getSize(); i++) {
                    if (this.inventory.getItem(i) == null) this.inventory.setItem(i, stack);
                }
            } else {
                for (int slot : slots) this.inventory.setItem(slot, stack);
            }
        }
    }
}
