package dev.yanianz.reminions.menu.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MenuConfig;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.core.item.ItemMenu;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.core.product.Product;
import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import dev.yanianz.reminions.placeholder.PlaceholderReplacer;
import dev.yanianz.reminions.utils.Text;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class UpgradeMenu extends MenuHolder {

    private static final List<Integer> RECIPE_SLOT_POSITIONS = List.of(10, 11, 12, 19, 20, 21, 28, 29, 30);

    private int viewLevel;
    private boolean isUpgradable;

    public UpgradeMenu(ReMinions plugin, PlayerMinions playerMinions, MenuConfig config, Object... placeholders) {
        super(plugin, playerMinions, config, placeholders);
    }

    @Override
    public void onOpen() {
        Minion minion = this.playerMinions.getMinionById(this.playerMinions.getViewMinion());
        if (minion == null) return;
        MinionConfig minionConfig = this.plugin.getMinionManager().get(minion.getName());
        if (minionConfig == null) return;
        int currentLevel = minion.getLevel();
        MinionUpgrade viewUpgrade = minionConfig.getUpgrade(this.viewLevel);
        if (viewUpgrade == null) return;
        double speedMult = minion.getSpeedMultiplier(this.plugin.getModifierManager());

        for (ItemMenu menuItem : this.menuConfig.items()) {
            String action = Optional.ofNullable(menuItem.getItemKey("action")).map(ItemKey::value).orElse("");
            ItemStack built = switch (action) {
                case "upgrade_accept" -> buildUpgradeAcceptItem(menuItem, minion, minionConfig, viewUpgrade, speedMult);
                case "minion_upgrades" -> buildMinionUpgradesItem(menuItem, minion, minionConfig, currentLevel);
                default -> menuItem.toBuild();
            };
            if (built != null) {
                for (int slot : menuItem.getSlots()) this.inventory.setItem(slot, built);
            }
        }

        populateRecipe(minion, minionConfig, viewUpgrade, speedMult);
        populateDecorations();
    }

    private ItemStack buildUpgradeAcceptItem(ItemMenu menuItem, Minion minion, MinionConfig minionConfig,
                                              MinionUpgrade viewUpgrade, double speedMult) {
        String skinId = minionConfig.getSkinLevel(this.viewLevel);
        MinionSkinConfig skin = Optional.ofNullable(this.plugin.getSkinManager().get(skinId))
                .orElse(this.plugin.getSkinManager().get(minion.getSkin()));
        if (skin == null) return null;
        ItemBuilder builder = new ItemBuilder(minionConfig.minionItem())
                .setMaterial(Material.PLAYER_HEAD)
                .addKey("action", "upgrade_accept")
                .setKey("item_id", menuItem.getId())
                .setHeadTexture(skin.getSlot(EquipmentSlot.HEAD).getHeadTexture());
        return builder.toBuild(
                "%roman_level%", Text.toRomanLevel(this.viewLevel),
                "%storage%", viewUpgrade.maxStorage(),
                "%collected%", minion.getCollected(),
                "%production_speed%", Text.format1(viewUpgrade.productionSpeed() / (1.0 + speedMult)));
    }

    private ItemStack buildMinionUpgradesItem(ItemMenu menuItem, Minion minion, MinionConfig minionConfig, int currentLevel) {
        Config config = this.plugin.getConfig0();
        String lockedTag   = config.getTag("upgrade_locked");
        String unlockedTag = config.getTag("upgrade_unlocked");
        String skinId = minionConfig.getSkinLevel(currentLevel);
        MinionSkinConfig skin = Optional.ofNullable(this.plugin.getSkinManager().get(skinId))
                .orElse(this.plugin.getSkinManager().get(minion.getSkin()));
        if (skin == null) return null;

        List<String> upgrades = new ArrayList<>();
        for (int lvl = 1; lvl <= minionConfig.getMaxLevel(); lvl++) {
            upgrades.add((lvl <= currentLevel ? unlockedTag : lockedTag).replace("%level%", Text.toRomanLevel(lvl)));
        }
        ItemBuilder builder = new ItemBuilder(menuItem)
                .setMaterial(Material.PLAYER_HEAD)
                .setHeadTexture(skin.getSlot(EquipmentSlot.HEAD).getHeadTexture());
        return builder.toBuild(
                "%minion_name%", minionConfig.name().replace("%roman_level%", Text.toRomanLevel(currentLevel)),
                "%minion_upgrades%", upgrades);
    }

    private void populateRecipe(Minion minion, MinionConfig minionConfig, MinionUpgrade upgrade, double speedMult) {
        MinionUpgrade.Requirement req = upgrade.requirement();
        if (req == null) return;
        Map<Character, MinionUpgrade.ItemRequirement> items = req.items();
        char[] shape = req.shape();

        for (int i = 0; i < shape.length && i < RECIPE_SLOT_POSITIONS.size(); i++) {
            char symbol = shape[i];
            int invSlot = RECIPE_SLOT_POSITIONS.get(i);
            if (symbol == '?') {
                MinionUpgrade prevUpgrade = minionConfig.getUpgrade(Math.max(1, upgrade.level() - 1));
                if (prevUpgrade == null) {
                    this.inventory.setItem(invSlot, new ItemStack(Material.BARRIER));
                } else {
                    this.inventory.setItem(invSlot, minionConfig.getMinionHead(
                            prevUpgrade.level(), minion.getCollected(),
                            prevUpgrade.maxStorage(), prevUpgrade.productionSpeed() / (1.0 + speedMult)));
                }
            } else {
                MinionUpgrade.ItemRequirement itemReq = items.get(symbol);
                if (itemReq != null) {
                    Product product = itemReq.product();
                    ItemStack stack = product.buildItem();
                    stack.setAmount(itemReq.amount());
                    this.inventory.setItem(invSlot, stack);
                }
            }
        }
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
            case "minion_menu" -> {
                this.playerMinions.setViewMinion(minion.getId());
                this.plugin.getMenuManager().openMenu("minion_menu", 0, 0, false, player,
                        "%minion_name%",
                        PlaceholderReplacer.replaceInlineText(minionConfig.name(), "%roman_level%", Text.toRomanLevel(minion.getLevel())));
            }
            case "upgrade_accept" -> {
                if (!this.isUpgradable) return;
                if (minionConfig.upgradeMinion(minion, this.plugin, this.playerMinions, player)) {
                    this.viewLevel++;
                    onOpen();
                    int titleLevel = minion.getLevel() == minionConfig.getMaxLevel()
                            ? minionConfig.getMaxLevel() : minion.getLevel() + 1;
                    NMSHandlerProvider.getHandler().updateInventoryTitle(
                            player.getOpenInventory(),
                            LegacyComponentSerializer.legacySection().serialize(
                                    Text.parseComponent(this.menuConfig.title(),
                                            "%minion_name%",
                                            minionConfig.name().replace("%roman_level%", Text.toRomanLevel(titleLevel)))));
                }
            }
            case "minion_upgrades" -> {
                this.playerMinions.setViewMinion(minion.getId());
                this.plugin.getMenuManager().openMenu("upgrades_menu", this.viewLevel, 0, false, player,
                        "%minion_name%",
                        PlaceholderReplacer.replaceInlineText(minionConfig.name(), "%roman_level%", ""));
            }
            case "close" -> player.closeInventory();
        }
    }

    public void setViewLevel(int level)       { this.viewLevel = level; }
    public void setUpgradable(boolean value)  { this.isUpgradable = value; }
}
