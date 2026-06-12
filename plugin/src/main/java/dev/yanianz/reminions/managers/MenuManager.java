package dev.yanianz.reminions.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MenuConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.item.ItemMenu;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.menu.MenuHolder;
import dev.yanianz.reminions.menu.impl.LayoutMenu;
import dev.yanianz.reminions.menu.impl.MinionMenu;
import dev.yanianz.reminions.menu.impl.StorageMenu;
import dev.yanianz.reminions.menu.impl.UpgradeMenu;
import dev.yanianz.reminions.menu.impl.UpgradesMenu;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.FileTreeHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Loads menu YAML configs and opens the appropriate {@link MenuHolder} per menu type. */
public class MenuManager extends FileTreeHandler<MenuConfig> {

    private static final List<String> DEFAULT_MENU_FILES = List.of(
            "minion_menu.yml", "layout_menu.yml", "storage_menu.yml",
            "upgrade_menu.yml", "upgrades_menu.yml");

    public MenuManager() {
        super(
                "menus",
                "menus",
                DEFAULT_MENU_FILES,
                (path, id) -> {},
                (path, id) -> DebugLogger.debug(String.format("Menu '%s' loaded successfully!", id)));
    }

    @Override
    public MenuConfig load(ConfigurationSection section, String menuId) {
        String title = section.getString("title", "");
        int rows = section.getInt("rows", 3);
        List<ItemMenu> items = loadItemMenus(section.getConfigurationSection("items"));
        List<ItemMenu> decorations = loadItemMenus(section.getConfigurationSection("decorations"));
        List<String> recipeSlotStrings = section.getStringList("slots_recipes");
        int[] recipeSlots = recipeSlotStrings.stream()
                .flatMapToInt(s -> Arrays.stream(parseSlots(s, "slots_recipes")))
                .toArray();
        return new MenuConfig(title, rows, items, decorations, recipeSlots);
    }

    private List<ItemMenu> loadItemMenus(ConfigurationSection section) {
        List<ItemMenu> result = new ArrayList<>();
        if (section == null) return result;
        for (String itemKey : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(itemKey);
            if (itemSection == null) continue;
            ItemBuilder builder = ItemBuilder.buildToFile(itemKey, itemSection);
            int[] slots = parseSlots(itemSection.getString("slot", ""), itemKey);
            result.add(new ItemMenu(builder, slots));
        }
        return result;
    }

    private int[] parseSlots(String slotString, String contextId) {
        List<Integer> slots = new ArrayList<>();
        String normalized = slotString.trim().toLowerCase();
        if (normalized.equals("fill")) {
            slots.add(-1);
        } else if (normalized.contains("-")) {
            String[] parts = normalized.split("-");
            try {
                int from = Integer.parseInt(parts[0].trim());
                int to   = Integer.parseInt(parts[1].trim());
                for (int i = from; i <= to; i++) slots.add(i);
            } catch (NumberFormatException e) {
                DebugLogger.warn("Invalid slot range '" + slotString + "' in item " + contextId);
            }
        } else {
            try {
                slots.add(Integer.parseInt(normalized));
            } catch (NumberFormatException e) {
                DebugLogger.warn("Invalid slot '" + slotString + "' in item " + contextId);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Opens a named menu for {@code player}.
     *
     * @param menuName   key into this manager (e.g. {@code "minion_menu"})
     * @param viewLevel  used by upgrade/upgrades menus as the current level
     * @param storageRows used by the storage menu for row count
     * @param upgradable used by the upgrade menu to control the upgrade button state
     * @param player     target player
     * @param placeholders varargs placeholder pairs forwarded to the menu
     */
    public void openMenu(String menuName, int viewLevel, int storageRows, boolean upgradable,
                         Player player, Object... placeholders) {
        PlayerMinions playerMinions = ReMinions.getPlugin().getPlayerManager().getById(player.getUniqueId());
        if (playerMinions == null) return;
        MenuConfig config = this.get(menuName);
        if (config == null) return;

        MenuHolder menu = switch (menuName) {
            case "minion_menu"   -> new MinionMenu(ReMinions.getPlugin(), playerMinions, config, placeholders);
            case "layout_menu"   -> new LayoutMenu(ReMinions.getPlugin(), playerMinions, config, placeholders);
            case "storage_menu"  -> new StorageMenu(ReMinions.getPlugin(), playerMinions, config, storageRows, placeholders);
            case "upgrade_menu"  -> {
                UpgradeMenu upgradeMenu = new UpgradeMenu(ReMinions.getPlugin(), playerMinions, config, placeholders);
                upgradeMenu.setUpgradable(upgradable);
                upgradeMenu.setViewLevel(viewLevel);
                yield upgradeMenu;
            }
            case "upgrades_menu" -> {
                UpgradesMenu upgradesMenu = new UpgradesMenu(ReMinions.getPlugin(), playerMinions, config, placeholders);
                upgradesMenu.setOldViewLevel(viewLevel);
                yield upgradesMenu;
            }
            default -> throw new IllegalStateException("Unknown menu type: " + menuName);
        };
        menu.onOpen();
        player.openInventory(menu.getInventory());
    }
}
