package dev.yanianz.reminions.menu.impl;

import java.util.ArrayList;
import java.util.List;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MenuConfig;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.core.item.ItemMenu;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.managers.ModifierManager;
import dev.yanianz.reminions.managers.SkinManager;
import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.placeholder.PlaceholderReplacer;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Menu showing all available upgrade levels as clickable minion-head icons. */
public class UpgradesMenu extends MenuHolder {

    private int oldViewLevel;

    public UpgradesMenu(ReMinions plugin, PlayerMinions playerMinions, MenuConfig config, Object... placeholders) {
        super(plugin, playerMinions, config, placeholders);
    }

    @Override
    public void onOpen() {
        Minion minion = this.playerMinions.getMinionById(this.playerMinions.getViewMinion());
        if (minion == null) return;
        MinionConfig minionConfig = this.plugin.getMinionManager().get(minion.getName());
        if (minionConfig == null) return;

        ItemBuilder headTemplate = getItemById("minion_head");
        if (headTemplate == null) return;

        // Populate non-head items first.
        for (ItemMenu menuItem : this.menuConfig.items()) {
            if (menuItem.getId().equals("minion_head")) continue;
            ItemStack built = menuItem.toBuild();
            for (int slot : menuItem.getSlots()) this.inventory.setItem(slot, built);
        }

        ModifierManager modMgr = this.plugin.getModifierManager();
        double speedMult = minion.getSpeedMultiplier(modMgr);
        SkinManager skinMgr  = this.plugin.getSkinManager();
        ItemBuilder minionItemTemplate = minionConfig.minionItem();

        int[] recipeSlots = this.menuConfig.slotsRecipes();
        for (int i = 0; i < recipeSlots.length; i++) {
            MinionUpgrade upgrade = minionConfig.getUpgrade(i + 1);
            if (upgrade == null) break;

            int level = upgrade.level();
            String skinId = minionConfig.getSkinLevel(level);
            MinionSkinConfig skin = skinId == null ? null : skinMgr.get(skinId);
            if (skin == null) continue;

            List<ItemKey> extraKeys = new ArrayList<>(headTemplate.getInternalKeys());
            extraKeys.add(new ItemKey("minion_menu_level", String.valueOf(level)));

            ItemStack headItem = new ItemBuilder(headTemplate.getId(), minionItemTemplate)
                    .addKeys(extraKeys)
                    .addLines(headTemplate.getLore())
                    .setHeadTexture(skin.getSlot(EquipmentSlot.HEAD).getHeadTexture())
                    .toBuild(
                            "%roman_level%", Text.toRomanLevel(level),
                            "%storage%", upgrade.maxStorage(),
                            "%collected%", minion.getCollected(),
                            "%production_speed%", Text.format1(upgrade.productionSpeed() / (1.0 + speedMult)));
            if (headItem != null) this.inventory.setItem(recipeSlots[i], headItem);
        }

        populateDecorations();
    }

    @Override
    public void onClick(Player player, ItemBuilder clickedItem, InventoryClickEvent event, int slot) {
        event.setCancelled(true);
        if (clickedItem == null) return;
        Minion minion = this.playerMinions.getMinionById(this.playerMinions.getViewMinion());
        if (minion == null) return;
        MinionConfig minionConfig = this.plugin.getMinionManager().get(minion.getName());
        if (minionConfig == null) return;
        ItemKey actionKey = clickedItem.getItemKey("action");
        if (actionKey == null) return;

        switch (actionKey.value()) {
            case "back" -> {
                this.playerMinions.setViewMinion(minion.getId());
                int displayLevel = this.oldViewLevel > minionConfig.getMaxLevel()
                        ? minion.getLevel() : this.oldViewLevel;
                this.plugin.getMenuManager().openMenu("upgrade_menu", this.oldViewLevel, 0, true, player,
                        "%minion_name%",
                        PlaceholderReplacer.replaceInlineText(minionConfig.name(), "%roman_level%", Text.toRomanLevel(displayLevel)));
            }
            case "close" -> player.closeInventory();
            case "minion_head" -> {
                ItemKey levelKey = ItemBuilder.getPersistentKey(event.getCurrentItem(), "minion_menu_level");
                if (levelKey == null) return;
                this.playerMinions.setViewMinion(minion.getId());
                int targetLevel = Integer.parseInt(levelKey.value());
                this.plugin.getMenuManager().openMenu("upgrade_menu", targetLevel, 0, false, player,
                        "%minion_name%",
                        PlaceholderReplacer.replaceInlineText(minionConfig.name(), "%roman_level%", Text.toRomanLevel(targetLevel)));
            }
        }
    }

    public void setOldViewLevel(int level) { this.oldViewLevel = level; }
}
