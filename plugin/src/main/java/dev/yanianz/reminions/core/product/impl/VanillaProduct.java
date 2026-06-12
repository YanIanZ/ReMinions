package dev.yanianz.reminions.core.product.impl;

import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.product.Product;
import org.bukkit.inventory.ItemStack;

public class VanillaProduct extends Product {
    private final ItemStack itemStack;

    public VanillaProduct(String id, double price, int amountStart, int amountEnd,
                          double chance, String requiredProduct, int requiredAmount,
                          SourceExpConfig expConfig, ItemStack itemStack) {
        super(id, price, amountStart, amountEnd, chance, requiredProduct, requiredAmount, expConfig);
        this.itemStack = itemStack;
    }

    @Override
    public ItemStack buildItem() {
        return new ItemStack(this.itemStack);
    }

    @Override
    public boolean matches(ItemStack stack) {
        return stack.isSimilar(this.buildItem());
    }
}
