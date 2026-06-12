package dev.yanianz.reminions.core.product.impl;

import dev.lone.itemsadder.api.CustomStack;
import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.product.Product;
import org.bukkit.inventory.ItemStack;

public class ItemsAdderProduct extends Product {
    private final String itemId;

    public ItemsAdderProduct(String id, double price, int amountStart, int amountEnd,
                             double chance, String requiredProduct, int requiredAmount,
                             SourceExpConfig expConfig, String itemId) {
        super(id, price, amountStart, amountEnd, chance, requiredProduct, requiredAmount, expConfig);
        this.itemId = itemId;
    }

    @Override
    public ItemStack buildItem() {
        CustomStack stack = CustomStack.getInstance(this.itemId);
        return stack == null ? null : stack.getItemStack();
    }

    @Override
    public boolean matches(ItemStack stack) {
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom != null && custom.getId().equals(this.itemId);
    }
}
