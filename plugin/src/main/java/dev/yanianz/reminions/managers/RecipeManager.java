package dev.yanianz.reminions.managers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

/** Tracks all plugin-registered shaped recipes and provides bulk add/remove. */
public class RecipeManager {

    private static final Set<NamespacedKey> registeredRecipes = new HashSet<>();

    private RecipeManager() {}

    /** Removes every recipe this plugin has previously registered from the server. */
    public static void clearRecipes() {
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        int removed = 0;
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof Keyed keyed && registeredRecipes.contains(keyed.getKey())) {
                iter.remove();
                removed++;
            }
        }
        DebugLogger.debug("Removed " + removed + " old BeeMinions recipes.");
        registeredRecipes.clear();
    }

    /**
     * Registers {@code recipe} with the server unless recipes are disabled or the key is duplicate.
     * No-ops silently if {@link ReMinions#isRecipesEnabled()} is false.
     */
    public static void registerRecipe(ShapedRecipe recipe) {
        if (!ReMinions.isRecipesEnabled()) return;
        NamespacedKey key = recipe.getKey();
        if (registeredRecipes.contains(key)) {
            DebugLogger.debug("Recipe " + key.getKey() + " already registered. Skipping duplicate.");
            return;
        }
        Bukkit.addRecipe(recipe);
        registeredRecipes.add(key);
        DebugLogger.debug("Registered recipe " + key.getKey());
    }

    public static Set<NamespacedKey> getRecipes() {
        return registeredRecipes;
    }
}
