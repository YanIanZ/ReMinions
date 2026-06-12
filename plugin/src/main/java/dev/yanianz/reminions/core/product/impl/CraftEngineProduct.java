package dev.yanianz.reminions.core.product.impl;

import java.util.Optional;
import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.product.Product;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.ItemManager;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;

public class CraftEngineProduct extends Product {
    private final String itemId;

    public CraftEngineProduct(String id, double price, int amountStart, int amountEnd,
                              double chance, String requiredProduct, int requiredAmount,
                              SourceExpConfig expConfig, String itemId) {
        super(id, price, amountStart, amountEnd, chance, requiredProduct, requiredAmount, expConfig);
        this.itemId = itemId;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ItemStack buildItem() {
        ItemManager raw = CraftEngine.instance().itemManager();
        Optional<CustomItem<ItemStack>> found = raw.getCustomItem(Key.of(this.itemId));
        return found.map(item -> (ItemStack) item.buildItemStack()).orElse(null);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean matches(ItemStack stack) {
        ItemManager raw = CraftEngine.instance().itemManager();
        Optional<CustomItem<ItemStack>> found = raw.getCustomItem(Key.of(this.itemId));
        return found.map(item -> item.id().asString().equals(this.itemId)).orElse(false);
    }
}
