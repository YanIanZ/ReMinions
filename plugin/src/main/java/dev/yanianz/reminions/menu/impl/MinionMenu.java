package dev.yanianz.reminions.menu.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.api.events.MinionItemsRemoveEvent;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MenuConfig;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.config.ModifierConfig;
import dev.yanianz.reminions.config.StorageConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.core.item.ItemMenu;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionModifierData;
import dev.yanianz.reminions.core.minion.MinionStorage;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.core.modifier.ModifierCategory;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.managers.ModifierManager;
import dev.yanianz.reminions.managers.SkinManager;
import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import dev.yanianz.reminions.placeholder.PlaceholderReplacer;
import dev.yanianz.reminions.utils.InventoryTransfer;
import dev.yanianz.reminions.utils.Text;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

public class MinionMenu extends MenuHolder {

    private static final List<Integer> INVENTORY_MINION_SLOTS =
            List.of(21, 22, 23, 24, 25, 30, 31, 32, 33, 34, 39, 40, 41, 42, 43);

    private static final ItemStack EMPTY_SLOT_ITEM = new ItemStack(Material.RED_STAINED_GLASS_PANE);

    static {
        EMPTY_SLOT_ITEM.editMeta(meta -> meta.displayName(Text.parseComponent("&7")));
    }

    private UUID viewMinion;

    public MinionMenu(ReMinions plugin, PlayerMinions playerMinions, MenuConfig config, Object... placeholders) {
        super(plugin, playerMinions, config, placeholders);
    }

    @Override
    public void onOpen() {
        Minion minion = this.playerMinions.getMinionById(this.playerMinions.getViewMinion());
        if (minion == null) return;
        MinionConfig minionConfig = this.plugin.getMinionManager().get(minion.getName());
        if (minionConfig == null) return;
        this.viewMinion = this.playerMinions.getViewMinion();
        this.populateDecorations();
        this.updateModifiersSection(minion, minionConfig);
        this.populateMinionInventory(minion);
    }

    public void updateModifiersSection(Minion minion, MinionConfig minionConfig) {
        int inventorySize    = this.inventory.getSize();
        Config config        = this.plugin.getConfig0();
        ModifierManager modMgr = this.plugin.getModifierManager();
        double speedMult     = minion.getSpeedMultiplier(modMgr);

        for (ItemMenu menuItem : this.menuConfig.items()) {
            for (int slot : menuItem.getSlots()) {
                if (slot < 0 || slot >= inventorySize) continue;

                ItemKey categoryAcceptKey = menuItem.getItemKey("modifier_category_accept");
                if (categoryAcceptKey == null) {
                    // Action-driven slot
                    String action = Optional.ofNullable(menuItem.getItemKey("action"))
                            .map(ItemKey::value).orElse("");
                    switch (action) {
                        case "upgrade_menu", "quick_upgrade" -> {
                            int nextLevel = Math.min(minion.getLevel() + 1, minionConfig.getMaxLevel());
                            MinionUpgrade nextUpgrade = minionConfig.getUpgrade(nextLevel);
                            if (nextUpgrade == null) break;

                            String newValuesTag       = config.getTag("new_upgrade_values");
                            String requirementLineTag = config.getTag("upgrade_requirement");
                            List<String> reqLines     = new ArrayList<>();

                            MinionUpgrade.Requirement req = nextUpgrade.requirement();
                            if (req != null) {
                                Map<Character, Long> symbolCounts = new String(req.shape())
                                        .chars()
                                        .mapToObj(c -> (char) c)
                                        .collect(Collectors.groupingBy(c -> (Character) c, Collectors.counting()));
                                req.items().forEach((symbol, itemReq) -> {
                                    long qty = itemReq.amount() * symbolCounts.getOrDefault(symbol, 0L);
                                    reqLines.add(PlaceholderReplacer.replaceInlineText(
                                            requirementLineTag,
                                            "%upgrade_item_display%", itemReq.display(),
                                            "%upgrade_item_amount%", String.valueOf(qty)));
                                });
                            }

                            double baseSpeed = minion.getProductionSpeed() / (1.0 + speedMult);
                            int currentMaxSlots = minion.getInventory().getMaxSlots();
                            String speedLine = PlaceholderReplacer.replaceInlineText(newValuesTag,
                                    "%upgrade_old_value%", Text.format1(baseSpeed),
                                    "%upgrade_new_value%", Text.format1(nextUpgrade.productionSpeed() / (1.0 + speedMult)));
                            String storageLine = PlaceholderReplacer.replaceInlineText(newValuesTag,
                                    "%upgrade_old_value%", currentMaxSlots,
                                    "%upgrade_new_value%", nextUpgrade.maxStorage());

                            this.inventory.setItem(slot, menuItem.toBuild(
                                    "%upgrade_production_speed%",
                                    nextUpgrade.productionSpeed() > 0.0 ? speedLine : Text.format1(baseSpeed),
                                    "%upgrade_max_storage%",
                                    nextUpgrade.maxStorage() > 0 ? storageLine : currentMaxSlots,
                                    "%upgrade_requirements%", reqLines));
                        }
                        case "minion_head" -> {
                            int currentLevel = minion.getLevel();
                            MinionUpgrade currentUpgrade = minionConfig.getUpgrade(currentLevel);
                            if (currentUpgrade == null) return;
                            String skinId = minionConfig.getSkinLevel(currentLevel);
                            MinionSkinConfig skin = skinId == null ? null : this.plugin.getSkinManager().get(skinId);
                            if (skin == null) return;
                            ItemStack head = minionConfig.getMinionHead(currentLevel,
                                    minion.getCollected(),
                                    minion.getInventory().getMaxSlots(),
                                    minion.getProductionSpeed() / (1.0 + speedMult));
                            if (head == null) return;
                            this.inventory.setItem(slot, head);
                        }
                        case "pickup_minion" -> this.inventory.setItem(slot, menuItem.toBuild(
                                "%roman_level%", Text.toRomanLevel(minion.getLevel()),
                                "%collected%", minion.getCollected()));
                        default -> this.inventory.setItem(slot, menuItem.toBuild());
                    }
                } else {
                    // Modifier / skin slot
                    ModifierCategory category = ModifierCategory.valueOf(categoryAcceptKey.value());
                    ItemStack slotItem;
                    if (category == ModifierCategory.SKIN) {
                        String skinId = minion.getSkin();
                        if (skinId != null) {
                            MinionSkinConfig skinConfig = this.plugin.getSkinManager().get(skinId);
                            slotItem = skinConfig != null ? skinConfig.getHead() : menuItem.toBuild();
                        } else {
                            slotItem = menuItem.toBuild();
                        }
                    } else {
                        MinionModifierData modData = minion.getModifiers().stream()
                                .filter(m -> m.getSlot() == slot).findFirst().orElse(null);
                        slotItem = buildSlotItem(menuItem, category, modData, modMgr, minion);
                    }
                    this.inventory.setItem(slot, slotItem);
                }
            }
        }
    }

    public void populateMinionInventory(Minion minion) {
        MinionInventory minionInv = minion.getInventory();
        List<ItemStack> stacks = new ArrayList<>(minionInv.getMaxSlots());

        for (MinionInventory.ItemData itemData : minionInv.getSnapshot()) {
            for (ItemStack stack : itemData.splitIntoStacks(itemData.getItem().getMaxStackSize())) {
                stack.editMeta(meta -> meta.getPersistentDataContainer()
                        .set(dev.yanianz.reminions.Keys.MINION_INVENTORY_ITEM,
                                PersistentDataType.STRING, itemData.getId().toString()));
                stacks.add(stack);
            }
        }

        for (int i = 0; i < INVENTORY_MINION_SLOTS.size(); i++) {
            int invSlot = INVENTORY_MINION_SLOTS.get(i);
            if (i < minionInv.getMaxSlots()) {
                this.inventory.setItem(invSlot, i < stacks.size() ? stacks.get(i) : null);
            } else {
                this.inventory.setItem(invSlot, EMPTY_SLOT_ITEM);
            }
        }
    }

    private ItemStack buildSlotItem(ItemMenu menuItem, ModifierCategory category,
                                    MinionModifierData modData, ModifierManager modMgr, Minion minion) {
        if (modData == null) return menuItem.toBuild();
        ModifierConfig modConfig = modMgr.get(modData.getName());
        if (modConfig == null) return menuItem.toBuild();
        if (modConfig.category() != category) {
            minion.removeModifier(modData);
            return menuItem.toBuild();
        }
        return modConfig.item().toBuild(
                List.of(new ItemKey("modifier_id", modData.getId().toString())),
                "%duration%", Text.getFormattedDurationLeft(modData.getAppliedAt(), modData.getDuration()),
                "%items_sold%", Text.format1(modData.getSoldItems()),
                "%money_earned%", Text.format1(modData.getMoneyEarned()));
    }

    @Override
    public void onClick(Player player, ItemBuilder clickedItem, InventoryClickEvent event, int slot) {
        Minion minion = this.playerMinions.getMinionById(this.playerMinions.getViewMinion());
        if (minion == null) return;
        MinionConfig minionConfig = this.plugin.getMinionManager().get(minion.getName());
        if (minionConfig == null) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv instanceof PlayerInventory) {
            boolean isModifierItem = ItemBuilder.getPersistentKey(event.getCurrentItem(), "modifier_item") != null
                    || ItemBuilder.getPersistentKey(event.getCursor(), "modifier_item") != null;
            boolean isSkinItem = ItemBuilder.getPersistentKey(event.getCurrentItem(), "skin_item") != null
                    || ItemBuilder.getPersistentKey(event.getCursor(), "skin_item") != null;
            boolean isShift = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
            if (!isModifierItem && !isSkinItem || isShift) event.setCancelled(true);
        } else {
            event.setCancelled(true);
            if (clickedItem != null) handleAction(player, clickedItem, event, minion, minionConfig, slot);
            handleModifierClick(player, clickedItem, event, minion, minionConfig, slot);
            handleMinionItemInventoryClick(minion, player, event);
            handleSkinClick(player, clickedItem, event, minion, minionConfig, slot);
        }
    }

    private void handleAction(Player player, ItemBuilder clickedItem, InventoryClickEvent event,
                               Minion minion, MinionConfig minionConfig, int slot) {
        ItemKey actionKey = clickedItem.getItemKey("action");
        if (actionKey == null) return;
        String action = actionKey.value();

        switch (action) {
            case "collect_all" -> {
                int moved = collectItems(player, minion);
                if (moved > 0) {
                    populateMinionInventory(minion);
                    player.updateInventory();
                    this.plugin.getConfig0().sendMessage(player, "collected_all_items", "%collected_amount%", moved);
                } else if (player.getInventory().firstEmpty() == -1) {
                    sendFullInventory(player);
                }
            }
            case "quick_upgrade" -> {
                if (minionConfig.upgradeMinion(minion, this.plugin, this.playerMinions, player)) {
                    onOpen();
                    NMSHandlerProvider.getHandler().updateInventoryTitle(
                            player.getOpenInventory(),
                            LegacyComponentSerializer.legacySection().serialize(
                                    Text.parseComponent(this.menuConfig.title(),
                                            "%minion_name%",
                                            minionConfig.name().replace("%roman_level%", Text.toRomanLevel(minion.getLevel())))));
                }
            }
            case "upgrade_menu" -> {
                this.playerMinions.setViewMinion(minion.getId());
                int nextLevel = Math.min(minion.getLevel() + 1, minionConfig.getMaxLevel());
                this.plugin.getMenuManager().openMenu(actionKey.value(), nextLevel, 0, true, player,
                        "%minion_name%",
                        PlaceholderReplacer.replaceInlineText(minionConfig.name(), "%roman_level%", Text.toRomanLevel(nextLevel)));
            }
            case "layout_menu" -> {
                this.playerMinions.setViewMinion(minion.getId());
                this.plugin.getMenuManager().openMenu("layout_menu", 0, 0, false, player,
                        "%minion_name%",
                        PlaceholderReplacer.replaceInlineText(minionConfig.name(), "%roman_level%", Text.toRomanLevel(minion.getLevel())));
            }
            case "close" -> player.closeInventory();
            case "pickup_minion" -> handlePickupMinion(player, minion, minionConfig);
        }
    }

    private void handlePickupMinion(Player player, Minion minion, MinionConfig minionConfig) {
        PlayerInventory playerInv = player.getInventory();
        int collected = collectItems(player, minion);
        if (collected == 0 && playerInv.firstEmpty() == -1) {
            sendFullInventory(player);
            return;
        }
        populateMinionInventory(minion);
        player.updateInventory();

        ModifierManager modMgr = this.plugin.getModifierManager();
        boolean didReturnModifiers = false;
        for (MinionModifierData modData : new ArrayList<>(minion.getModifiers())) {
            if (modData.isExpired()) continue;
            ModifierConfig modConfig = modMgr.get(modData.getName());
            if (modConfig == null || !modConfig.unbreakable()) continue;
            ItemStack returnItem = buildReturnableModifierItem(modConfig, modData, modData.getSoldItems(), modData.getMoneyEarned());
            if (!addToInventory(playerInv, player, returnItem)) return;
            minion.removeModifier(modData);
            didReturnModifiers = true;
        }
        if (didReturnModifiers) updateModifiersSection(minion, minionConfig);

        if (minion.getSkin() != null) {
            SkinManager skinMgr = this.plugin.getSkinManager();
            MinionSkinConfig customSkin = skinMgr.get(minion.getSkin());
            if (customSkin == null) return;
            if (!addToInventory(playerInv, player, customSkin.getHead())) return;
            minion.setSkin(null);
            minion.despawn();
            MinionSkinConfig baseSkin = skinMgr.get(minion.getSkinLevel());
            if (baseSkin == null) return;
            minion.spawn(baseSkin);
            updateModifiersSection(minion, minionConfig);
        }

        ItemStack minionHead = minionConfig.getMinionHead(minion);
        boolean headAlreadyAdded = false;
        MinionStorage storage = minion.getStorage();
        if (storage != null) {
            StorageConfig storageConfig = this.plugin.getStorageManager().get(storage.name());
            if (storageConfig == null) return;
            ItemStack storageItem = storageConfig.item().toBuild(
                    "%storage%", storageConfig.maxStorage(), "%block_skin%", Text.prettyMaterial(storageConfig.blockSkin()));
            List<ItemStack> dropPayload = InventoryTransfer.flattenWithExtra(storage.inventory(), storageItem);
            if (minionHead != null) {
                dropPayload.add(minionHead);
                headAlreadyAdded = true;
            }
            if (!InventoryTransfer.addAll(playerInv, dropPayload)) {
                sendFullInventory(player);
                return;
            }
            storage.location().toLocation().getBlock().setType(Material.AIR);
            minion.setStorage(null);
        }

        player.closeInventory();
        if (!headAlreadyAdded && minionHead != null && !addToInventory(playerInv, player, minionHead)) return;

        this.plugin.getPlayerManager().removeMinion(this.playerMinions, minion);
        this.plugin.getConfig0().sendMessage(player, "pickup_success",
                "%minion_name%", minionConfig.name().replace("%roman_level%", Text.toRomanLevel(minion.getLevel())));
    }

    private boolean addToInventory(PlayerInventory playerInv, Player player, ItemStack item) {
        if (item == null) return true;
        if (!InventoryTransfer.canFit(playerInv, List.of(item))) {
            sendFullInventory(player);
            return false;
        }
        HashMap<Integer, ItemStack> overflow = playerInv.addItem(item.clone());
        if (!overflow.isEmpty()) {
            sendFullInventory(player);
            return false;
        }
        return true;
    }

    private void sendFullInventory(Player player) {
        this.plugin.getConfig0().sendMessage(player, "inventory_full");
    }

    private int collectItems(Player player, Minion minion) {
        MinionInventory minionInv = minion.getInventory();
        InventoryTransfer.TransferResult result = InventoryTransfer.collect(player.getInventory(), minionInv);
        MinionItemsRemoveEvent.ResultState state = result.movedAny()
                ? MinionItemsRemoveEvent.ResultState.SUCESS : MinionItemsRemoveEvent.ResultState.FAILED;
        Bukkit.getPluginManager().callEvent(new MinionItemsRemoveEvent(
                player, minion, minionInv,
                result.removedItems().toArray(new MinionInventory.ItemData[0]), state));
        return result.totalMoved();
    }

    private void handleMinionItemInventoryClick(Minion minion, Player player, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;
        ItemKey itemKey = ItemBuilder.getPersistentKey(clickedItem, "minion_inventory_item");
        if (itemKey == null) return;
        MinionInventory minionInv = minion.getInventory();
        MinionInventory.ItemData itemData = minionInv.getItemData(UUID.fromString(itemKey.value()));
        if (itemData == null) return;
        if (!InventoryTransfer.transferExact(player.getInventory(), minionInv, itemData, clickedItem.getAmount())) {
            sendFullInventory(player);
        } else {
            Bukkit.getPluginManager().callEvent(new MinionItemsRemoveEvent(
                    player, minion, minionInv,
                    new MinionInventory.ItemData[]{MinionInventory.ItemData.of(itemData.getItem(), clickedItem.getAmount())},
                    MinionItemsRemoveEvent.ResultState.SUCESS));
            populateMinionInventory(minion);
            player.updateInventory();
        }
    }

    private void handleModifierClick(Player player, ItemBuilder clickedItem, InventoryClickEvent event,
                                      Minion minion, MinionConfig minionConfig, int slot) {
        ItemKey modifierIdKey = ItemBuilder.getPersistentKey(event.getCurrentItem(), "modifier_id");
        if (modifierIdKey != null) {
            handleModifierRemoval(player, event.getClick(), minion, minionConfig, modifierIdKey);
            return;
        }
        if (clickedItem == null) return;
        ItemKey modifierItemKey   = ItemBuilder.getPersistentKey(event.getCursor(), "modifier_item");
        ItemKey categoryAcceptKey = clickedItem.getItemKey("modifier_category_accept");
        if (modifierItemKey != null && categoryAcceptKey != null) {
            handleModifierPlacement(player, minion, minionConfig, slot, modifierItemKey, categoryAcceptKey);
        }
    }

    private void handleSkinClick(Player player, ItemBuilder clickedItem, InventoryClickEvent event,
                                  Minion minion, MinionConfig minionConfig, int slot) {
        SkinManager skinMgr = this.plugin.getSkinManager();
        ItemKey currentSkinKey = ItemBuilder.getPersistentKey(event.getCurrentItem(), "skin_item");
        if (currentSkinKey != null) {
            MinionSkinConfig currentSkin = skinMgr.get(currentSkinKey.value());
            if (currentSkin != null && !isInventoryFull(player)) {
                minion.setSkin(null);
                minion.despawn();
                MinionSkinConfig baseSkin = skinMgr.get(minion.getSkinLevel());
                if (baseSkin != null) minion.spawn(baseSkin);
                addToInventory(player.getInventory(), player, currentSkin.getHead());
                onOpen();
            }
            return;
        }
        ItemKey cursorSkinKey = ItemBuilder.getPersistentKey(event.getCursor(), "skin_item");
        if (cursorSkinKey == null) return;
        MinionSkinConfig cursorSkin = skinMgr.get(cursorSkinKey.value());
        if (cursorSkin == null) return;
        if (minionConfig.categorySkin() != null && !cursorSkin.getCategory().equals(minionConfig.categorySkin())) {
            this.plugin.getConfig0().sendMessage(player, "minion_category_not_match");
            player.closeInventory();
            return;
        }
        player.setItemOnCursor(null);
        minion.setSkin(cursorSkinKey.value());
        minion.despawn();
        minion.spawn(cursorSkin);
        onOpen();
    }

    private void handleModifierRemoval(Player player, ClickType clickType, Minion minion,
                                        MinionConfig minionConfig, ItemKey modifierIdKey) {
        ModifierManager modMgr = this.plugin.getModifierManager();
        MinionModifierData modData = minion.getModifierById(UUID.fromString(modifierIdKey.value()));
        if (modData == null) return;
        ModifierConfig modConfig = modMgr.get(modData.getName());
        if (modConfig == null) return;

        if (modData.getType() == ModifierType.AUTO_SELL && clickType == ClickType.RIGHT) {
            Economy economy = this.plugin.getEconomy();
            if (economy == null) return;
            double money = modData.getMoneyEarned();
            if (money < 0.0) return;
            this.plugin.getConfig0().sendMessage(player, "collected_money_auto_sell", "%money_earned%", Text.format1(money));
            economy.depositPlayer(player, money);
            modData.setMoneyEarned(0.0);
            updateModifiersSection(minion, minionConfig);
        } else if (!isInventoryFull(player)) {
            minion.removeModifier(modData);
            if (modConfig.unbreakable()) {
                addToInventory(player.getInventory(), player,
                        buildReturnableModifierItem(modConfig, modData, modData.getSoldItems(), modData.getMoneyEarned()));
            }
            updateModifiersSection(minion, minionConfig);
        }
    }

    private void handleModifierPlacement(Player player, Minion minion, MinionConfig minionConfig,
                                          int slot, ItemKey modifierItemKey, ItemKey categoryAcceptKey) {
        ModifierManager modMgr = this.plugin.getModifierManager();
        ModifierConfig modConfig = modMgr.get(modifierItemKey.value());
        if (modConfig == null) return;
        ModifierCategory category = ModifierCategory.valueOf(categoryAcceptKey.value());
        if (modConfig.category() != category) {
            this.plugin.getConfig0().sendMessage(player, "invalid_modifier_category");
            return;
        }
        boolean slotOccupied = minion.getModifiers().stream().anyMatch(m -> m.getSlot() == slot);
        if (slotOccupied) return;
        ItemStack cursorItem = player.getItemOnCursor();
        if (cursorItem == null || cursorItem.getType().isAir() || cursorItem.getAmount() <= 0) return;
        long durationMillis = calculateDurationMillis(modConfig, cursorItem, getAmountToConsume(category, modConfig, cursorItem));
        // A returned fuel item that ticked down to zero in the player's inventory must not be
        // re-applied as a free no-op — drop the placement and let the player throw it away.
        if (category == ModifierCategory.FUEL && durationMillis == 0L) {
            this.plugin.getConfig0().sendMessage(player, "fuel_expired");
            return;
        }
        int amount = getAmountToConsume(category, modConfig, cursorItem);
        consumeCursorItems(player, cursorItem, amount);
        minion.addModifier(new MinionModifierData(
                UUID.randomUUID(), modConfig.id(), modConfig.type(), slot,
                System.currentTimeMillis(), durationMillis));
        updateModifiersSection(minion, minionConfig);
    }

    private int getAmountToConsume(ModifierCategory category, ModifierConfig modConfig, ItemStack cursorItem) {
        if (category != ModifierCategory.FUEL || modConfig.duration() <= 0) return 1;
        // Carried-back fuel already encodes its own remaining lifetime on the item — eating
        // the whole stack would multiply the same residual time by N, which is not what the
        // player expects. Fresh catalog fuel still consumes the stack and adds N × duration.
        if (ItemBuilder.getPersistentKey(cursorItem, "modifier_expiry") != null
                || ItemBuilder.getPersistentKey(cursorItem, "modifier_duration") != null) {
            return 1;
        }
        return cursorItem.getAmount();
    }

    private void consumeCursorItems(Player player, ItemStack cursorItem, int amount) {
        int remaining = cursorItem.getAmount() - Math.max(1, amount);
        if (remaining > 0) {
            cursorItem.setAmount(remaining);
            player.setItemOnCursor(cursorItem);
        } else {
            player.setItemOnCursor(null);
        }
    }

    private ItemStack buildReturnableModifierItem(ModifierConfig modConfig, MinionModifierData modData,
                                                   long soldItems, double moneyEarned) {
        // FUEL modifiers track an absolute expiry epoch on the item PDC so the lore continues
        // to decrement in real time while the player is carrying the item back to a chest.
        // The applied modifier is still removed from the minion as before — only the
        // "remaining duration" display is what stays live in the player's inventory.
        long expiry = computeAbsoluteExpiry(modData);
        long remaining = expiry < 0L ? -1L : Math.max(0L, expiry - System.currentTimeMillis());
        return modConfig.item().toBuild(
                List.of(new ItemKey("modifier_expiry", String.valueOf(expiry))),
                "%duration%", Text.getFormattedDurationLeft(System.currentTimeMillis(), remaining),
                "%items_sold%", Text.format1(soldItems),
                "%money_earned%", Text.format1(moneyEarned));
    }

    /** Wall-clock epoch when {@code modData} runs out, or {@code -1L} if it has no duration cap. */
    private long computeAbsoluteExpiry(MinionModifierData modData) {
        if (modData.getDuration() < 0L) return -1L;
        long expiry = modData.getAppliedAt() + modData.getDuration();
        return expiry > 0L ? expiry : 0L;
    }

    private long calculateDurationMillis(ModifierConfig modConfig, ItemStack item, int amount) {
        if (modConfig.duration() < 0) return -1L;
        long carriedRemaining = readRemainingFromItem(item);
        if (carriedRemaining >= 0L) {
            // Item came from a previous placement; honour whatever clock has already ticked
            // down while it sat in the player's inventory. `amount` is forced to 1 upstream
            // for fuel items that carry a live PDC entry.
            return carriedRemaining;
        }
        long baseDuration = modConfig.duration() * 1000L;
        long qty = Math.max(1, amount);
        return baseDuration > Long.MAX_VALUE / qty ? Long.MAX_VALUE : baseDuration * qty;
    }

    /**
     * Returns the live "remaining millis" encoded on the item's PDC, or {@code -1L} when the
     * item has no expiry mark (i.e. it's a fresh fuel straight off the catalog). Supports
     * legacy {@code modifier_duration} (relative) entries as a backwards-compat fallback.
     */
    private long readRemainingFromItem(ItemStack item) {
        ItemKey expiryKey = ItemBuilder.getPersistentKey(item, "modifier_expiry");
        if (expiryKey != null) {
            try {
                long expiry = Long.parseLong(expiryKey.value());
                if (expiry < 0L) return -1L;
                return Math.max(0L, expiry - System.currentTimeMillis());
            } catch (NumberFormatException ignored) {}
        }
        ItemKey legacyKey = ItemBuilder.getPersistentKey(item, "modifier_duration");
        if (legacyKey != null) {
            try {
                long stored = Long.parseLong(legacyKey.value());
                if (stored >= 0L) return stored;
            } catch (NumberFormatException ignored) {}
        }
        return -1L;
    }

    private boolean isInventoryFull(Player player) {
        if (player.getInventory().firstEmpty() == -1) {
            sendFullInventory(player);
            return true;
        }
        return false;
    }

    public UUID getViewMinion() {
        return this.viewMinion;
    }
}
