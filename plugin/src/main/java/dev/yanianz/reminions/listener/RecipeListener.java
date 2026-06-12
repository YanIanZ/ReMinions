package dev.yanianz.reminions.listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import dev.yanianz.reminions.Keys;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.minion.MinionMeta;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.RecipeChoice.ExactChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Implements custom validation + consumption for our level-aware minion crafting recipes.
 *
 * <p>The crafting grid for an upgraded minion ({@code *_lvl2}, {@code *_lvl3}, ...) must
 * contain a minion item of the previous level — the prepare-craft hook checks this and the
 * craft hook performs the exact ingredient deduction (vanilla can't decrement by &gt; 1).</p>
 */
public class RecipeListener implements Listener {

    /** Crafting table is a fixed 3×3 grid; used to map (row,col) → slot index. */
    private static final int GRID_WIDTH = 3;

    // ─────────────────────────────────────────────────────────────────────────────
    // PrepareItemCraft: blanks the result if the matrix is missing the prerequisite
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (!(inventory.getRecipe() instanceof ShapedRecipe recipe)) return;
        NamespacedKey recipeKey = recipe.getKey();
        if (recipeKey == null || !Keys.NAMESPACE.equals(recipeKey.getNamespace())) return;

        String[] parts = recipeKey.getKey().split("_lvl");
        if (parts.length != 2) {
            inventory.setResult(null);
            return;
        }
        int targetLevel = Integer.parseInt(parts[1]);
        ItemStack[] matrix = inventory.getMatrix();

        if (!this.matrixHasPrerequisiteMinion(matrix, targetLevel)) {
            inventory.setResult(null);
            return;
        }

        if (!this.matrixHasEnoughOfEachExactChoice(matrix, recipe)) {
            inventory.setResult(null);
        }
    }

    /** Either the recipe is for level 1, or the matrix contains a (level-1) minion player head. */
    private boolean matrixHasPrerequisiteMinion(ItemStack[] matrix, int targetLevel) {
        if (targetLevel == 1) return true;
        return Arrays.stream(matrix)
                .filter(Objects::nonNull)
                .filter(item -> item.getType() == Material.PLAYER_HEAD)
                .map(item -> ItemBuilder.getPersistentKey(item, "minion_item"))
                .filter(Objects::nonNull)
                .map(key -> ItemBuilder.GSON.fromJson(key.value(), MinionMeta.class))
                .anyMatch(meta -> meta != null && meta.getLevel() == targetLevel - 1);
    }

    /** Each ExactChoice ingredient must appear with at least the required total amount across all matching slots. */
    private boolean matrixHasEnoughOfEachExactChoice(ItemStack[] matrix, ShapedRecipe recipe) {
        Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
        for (Entry<Character, RecipeChoice> entry : choiceMap.entrySet()) {
            char symbol = entry.getKey();
            RecipeChoice choice = entry.getValue();
            if (!(choice instanceof ExactChoice exact) || exact.getChoices().isEmpty()) continue;

            ItemStack expected = exact.getChoices().getFirst();
            int perSlotNeeded = expected.getAmount();
            List<Integer> slots = getSlotsForSymbol(recipe, symbol);
            int totalNeeded = perSlotNeeded * slots.size();

            int totalFound = 0;
            for (int slot : slots) {
                ItemStack inSlot = matrix[slot];
                if (inSlot != null && expected.isSimilar(inSlot)) {
                    totalFound += inSlot.getAmount();
                }
            }
            if (totalFound < totalNeeded) {
                return false;
            }
        }
        return true;
    }

    /** Lists all crafting-grid slot indices that the given {@code symbol} occupies in the recipe shape. */
    private static List<Integer> getSlotsForSymbol(ShapedRecipe recipe, char symbol) {
        ArrayList<Integer> slots = new ArrayList<>();
        String[] shape = recipe.getShape();
        for (int row = 0; row < shape.length; row++) {
            String line = shape[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == symbol) {
                    slots.add(row * GRID_WIDTH + col);
                }
            }
        }
        return slots;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CraftItem: replace vanilla decrement (always -1) with our exact amount logic
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof ShapedRecipe recipe)) return;
        NamespacedKey recipeKey = recipe.getKey();
        if (recipeKey == null || !Keys.NAMESPACE.equals(recipeKey.getNamespace())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        boolean shiftClick = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
        event.setCancelled(true);
        if (!shiftClick) {
            this.craftOnce(player, inventory, recipe, result);
        } else {
            this.craftMax(player, inventory, recipe, result);
        }
    }

    private void craftOnce(Player player, CraftingInventory inventory, ShapedRecipe recipe, ItemStack result) {
        if (!this.consumeIngredients(inventory, recipe)) return;
        ItemStack copy = result.clone();
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(copy);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.updateInventory();
    }

    private void craftMax(Player player, CraftingInventory inventory, ShapedRecipe recipe, ItemStack result) {
        int maxBatches = this.getCraftableAmount(inventory, recipe);
        if (maxBatches <= 0) return;
        for (int i = 0; i < maxBatches && this.consumeIngredients(inventory, recipe); i++) {
            ItemStack copy = result.clone();
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(copy);
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        player.updateInventory();
    }

    /** Returns the highest number of full crafts the current matrix can support, or 0 if any ingredient is missing. */
    private int getCraftableAmount(CraftingInventory inventory, ShapedRecipe recipe) {
        Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
        String[] shape = recipe.getShape();
        ItemStack[] matrix = inventory.getMatrix();
        int max = Integer.MAX_VALUE;

        for (int row = 0; row < shape.length; row++) {
            char[] line = shape[row].toCharArray();
            for (int col = 0; col < line.length; col++) {
                char symbol = line[col];
                if (symbol == ' ' || !choiceMap.containsKey(symbol)) continue;
                RecipeChoice choice = choiceMap.get(symbol);
                if (!(choice instanceof ExactChoice exact) || exact.getChoices().isEmpty()) continue;

                ItemStack expected = exact.getChoices().getFirst();
                int perSlotNeeded = expected.getAmount();
                int slotIndex = row * GRID_WIDTH + col;
                ItemStack inSlot = matrix[slotIndex];
                if (inSlot == null || !expected.isSimilar(inSlot)) {
                    return 0;
                }
                int times = inSlot.getAmount() / perSlotNeeded;
                max = Math.min(max, times);
            }
        }
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    /** Decrements every ingredient slot by its recipe-required amount. Always returns {@code true}. */
    private boolean consumeIngredients(CraftingInventory inventory, ShapedRecipe recipe) {
        ItemStack[] matrix = inventory.getMatrix();
        String[] shape = recipe.getShape();
        Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();

        for (int row = 0; row < shape.length; row++) {
            char[] line = shape[row].toCharArray();
            for (int col = 0; col < line.length; col++) {
                char symbol = line[col];
                if (symbol == ' ' || symbol == '?') continue;
                RecipeChoice choice = choiceMap.get(symbol);
                if (!(choice instanceof ExactChoice exact) || exact.getChoices().isEmpty()) continue;

                ItemStack expected = exact.getChoices().getFirst();
                int perSlotNeeded = expected.getAmount();
                int slotIndex = row * GRID_WIDTH + col;
                if (slotIndex < 0 || slotIndex >= matrix.length) continue;
                ItemStack inSlot = matrix[slotIndex];
                if (inSlot == null || !expected.isSimilar(inSlot)) continue;
                int remaining = inSlot.getAmount() - perSlotNeeded;
                matrix[slotIndex] = remaining <= 0 ? null : new ItemStack(inSlot.getType(), remaining);
            }
        }
        inventory.setMatrix(matrix);
        return true;
    }
}
