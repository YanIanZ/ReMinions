package dev.yanianz.reminions.menu.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.api.events.MinionItemsRemoveEvent;
import dev.yanianz.reminions.config.MenuConfig;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.StorageConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.utils.InventoryTransfer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class StorageMenu extends MenuHolder {

    private UUID viewMinion;

    public StorageMenu(ReMinions plugin, PlayerMinions playerMinions, MenuConfig config,
                       int rows, Object... placeholders) {
        super(plugin, playerMinions, config, rows, placeholders);
    }

    @Override
    public void onOpen() {
        Minion minion = this.playerMinions.getMinionById(this.playerMinions.getViewMinion());
        if (minion == null || minion.getStorage() == null) return;
        MinionConfig minionConfig = this.plugin.getMinionManager().get(minion.getName());
        if (minionConfig == null) return;
        this.viewMinion = this.playerMinions.getViewMinion();
        populateDecorations();
        populateItems();
        populateMinionInventory(minion);
    }

    public void populateMinionInventory(Minion minion) {
        MinionStorage storage = minion.getStorage();
        if (storage == null) return;
        StorageConfig storageConfig = this.plugin.getStorageManager().get(storage.name());
        if (storageConfig == null) return;

        MinionInventory storageInv = storage.inventory();
        List<ItemStack> stacks = new ArrayList<>(storageInv.getMaxSlots());

        for (MinionInventory.ItemData itemData : storageInv.getSnapshot()) {
            for (ItemStack stack : itemData.splitIntoStacks(itemData.getItem().getMaxStackSize())) {
                stack.editMeta(meta -> meta.getPersistentDataContainer()
                        .set(dev.yanianz.reminions.Keys.MINION_INVENTORY_ITEM,
                                PersistentDataType.STRING, itemData.getId().toString()));
                stacks.add(stack);
            }
        }

        List<Integer> pattern = storageConfig.pattern();
        for (int i = 0; i < pattern.size(); i++) {
            int invSlot = pattern.get(i);
            if (i < storageInv.getMaxSlots()) {
                this.inventory.setItem(invSlot, i < stacks.size() ? stacks.get(i) : null);
            }
        }
    }

    @Override
    public void onClick(Player player, ItemBuilder clickedItem, InventoryClickEvent event, int slot) {
        event.setCancelled(true);
        Minion minion = this.playerMinions.getMinionById(this.viewMinion);
        if (minion == null || minion.getStorage() == null) return;
        if (clickedItem == null || !handleAction(player, clickedItem, minion)) {
            handleMinionItemInventoryClick(minion, player, event);
        }
    }

    private boolean handleAction(Player player, ItemBuilder clickedItem, Minion minion) {
        ItemKey actionKey = clickedItem.getItemKey("action");
        if (actionKey == null) return false;
        switch (actionKey.value()) {
            case "collect_all" -> collectAll(player, minion);
            case "close"       -> player.closeInventory();
            default            -> { return false; }
        }
        return true;
    }

    private void collectAll(Player player, Minion minion) {
        MinionStorage storage = minion.getStorage();
        if (storage == null) return;
        MinionInventory storageInv = storage.inventory();
        if (storageInv == null || storageInv.isEmpty()) return;

        InventoryTransfer.TransferResult result = InventoryTransfer.collect(player.getInventory(), storageInv);
        MinionItemsRemoveEvent.ResultState state = result.movedAny()
                ? MinionItemsRemoveEvent.ResultState.SUCESS : MinionItemsRemoveEvent.ResultState.FAILED;
        Bukkit.getPluginManager().callEvent(new MinionItemsRemoveEvent(
                player, minion, storageInv,
                result.removedItems().toArray(new MinionInventory.ItemData[0]), state));

        if (result.movedAny()) {
            populateMinionInventory(minion);
            player.updateInventory();
            this.plugin.getConfig0().sendMessage(player, "storage_collected_all_items",
                    "%collected_amount%", result.totalMoved());
        } else {
            this.plugin.getConfig0().sendMessage(player, "inventory_full");
        }
    }

    private void handleMinionItemInventoryClick(Minion minion, Player player, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;
        ItemKey itemKey = ItemBuilder.getPersistentKey(clickedItem, "minion_inventory_item");
        if (itemKey == null) return;
        MinionStorage storage = minion.getStorage();
        if (storage == null) return;
        MinionInventory storageInv = storage.inventory();
        MinionInventory.ItemData itemData = storageInv.getItemData(UUID.fromString(itemKey.value()));
        if (itemData == null) return;

        if (!InventoryTransfer.transferExact(player.getInventory(), storageInv, itemData, clickedItem.getAmount())) {
            this.plugin.getConfig0().sendMessage(player, "inventory_full");
        } else {
            Bukkit.getPluginManager().callEvent(new MinionItemsRemoveEvent(
                    player, minion, storageInv,
                    new MinionInventory.ItemData[]{MinionInventory.ItemData.of(itemData.getItem(), clickedItem.getAmount())},
                    MinionItemsRemoveEvent.ResultState.SUCESS));
            populateMinionInventory(minion);
            player.updateInventory();
        }
    }

    public UUID getViewMinion() { return this.viewMinion; }
}
