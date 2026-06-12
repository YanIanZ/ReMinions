package dev.yanianz.reminions.menu.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MenuConfig;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemKey;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.placeholder.PlaceholderReplacer;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public class LayoutMenu extends MenuHolder {

    /** Maps non-item blocks to their item-form equivalent for display in the GUI. */
    private static final Map<Material, Material> BLOCK_TO_ITEM_MAP = new HashMap<>();

    static {
        BLOCK_TO_ITEM_MAP.put(Material.WATER,    Material.WATER_BUCKET);
        BLOCK_TO_ITEM_MAP.put(Material.LAVA,     Material.LAVA_BUCKET);
        BLOCK_TO_ITEM_MAP.put(Material.CARROTS,  Material.CARROT);
        BLOCK_TO_ITEM_MAP.put(Material.POTATOES, Material.POTATO);
        BLOCK_TO_ITEM_MAP.put(Material.BEETROOTS, Material.BEETROOT);
    }

    private BukkitTask animationTask;
    private int blockIndex;

    /** Grid display constants: center row offset and center column offset in a 6×9 inventory. */
    private static final int GRID_ROW_OFFSET = 2;
    private static final int GRID_COL_OFFSET = 4;

    public LayoutMenu(ReMinions plugin, PlayerMinions playerMinions, MenuConfig config, Object... placeholders) {
        super(plugin, playerMinions, config, placeholders);
    }

    @Override
    public void onOpen() {
        Minion minion = this.playerMinions.getMinionById(this.playerMinions.getViewMinion());
        if (minion == null) return;
        MinionConfig minionConfig = this.plugin.getMinionManager().get(minion.getName());
        if (minionConfig == null) return;

        int radius = Math.min(minion.getBaseRadius(), 2);
        List<Material> blockCycle = new ArrayList<>(minionConfig.blocksCheckAround());

        this.animationTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            Material currentBlock = blockCycle.get(this.blockIndex % blockCycle.size());
            Material displayMaterial = BLOCK_TO_ITEM_MAP.getOrDefault(currentBlock, currentBlock);

            for (int dRow = -radius; dRow <= radius; dRow++) {
                for (int dCol = -radius; dCol <= radius; dCol++) {
                    int row = GRID_ROW_OFFSET + dCol;
                    int col = GRID_COL_OFFSET + dRow;
                    if (row >= 0 && row < 6 && col >= 0 && col < 9) {
                        this.inventory.setItem(row * 9 + col, new ItemStack(displayMaterial));
                    }
                }
            }
            this.blockIndex++;
        }, 0L, 20L);

        populateItems();
        populateDecorations();
    }

    @Override
    public void onClick(Player player, ItemBuilder clickedItem, InventoryClickEvent event, int slot) {
        event.setCancelled(true);
        if (clickedItem == null) return;
        ItemKey actionKey = clickedItem.getItemKey("action");
        if (actionKey == null) return;
        Minion minion = this.playerMinions.getMinionById(this.playerMinions.getViewMinion());
        if (minion == null) return;
        MinionConfig minionConfig = this.plugin.getMinionManager().get(minion.getName());
        if (minionConfig == null) return;

        switch (actionKey.value()) {
            case "minion_menu" -> {
                this.playerMinions.setViewMinion(minion.getId());
                this.plugin.getMenuManager().openMenu("minion_menu", 0, 0, false, player,
                        "%minion_name%",
                        PlaceholderReplacer.replaceInlineText(minionConfig.name(), "%roman_level%", Text.toRomanLevel(minion.getLevel())));
            }
            case "close" -> player.closeInventory();
        }
    }

    public BukkitTask getAnimationTask() {
        return this.animationTask;
    }
}
