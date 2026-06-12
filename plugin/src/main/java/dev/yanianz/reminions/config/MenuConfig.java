package dev.yanianz.reminions.config;

import java.util.List;
import dev.yanianz.reminions.core.item.ItemMenu;

public record MenuConfig(String title, int rows, List<ItemMenu> items, List<ItemMenu> decorations, int[] slotsRecipes) {
}
