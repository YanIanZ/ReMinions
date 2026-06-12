package dev.yanianz.reminions.core.product.impl;

import com.willfp.ecoitems.items.EcoItem;
import com.willfp.ecoitems.items.EcoItems;
import com.willfp.ecoitems.items.ItemUtilsKt;
import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.product.Product;
import org.bukkit.inventory.ItemStack;

public class EcoItemProduct extends Product {
    private final String itemId;

    public EcoItemProduct(String id, double price, int amountStart, int amountEnd,
                          double chance, String requiredProduct, int requiredAmount,
                          SourceExpConfig expConfig, String itemId) {
        super(id, price, amountStart, amountEnd, chance, requiredProduct, requiredAmount, expConfig);
        this.itemId = itemId;
    }

    @Override
    public ItemStack buildItem() {
        EcoItem item = EcoItems.INSTANCE.getByID(this.itemId);
        return item == null ? null : item.getItemStack();
    }

    @Override
    public boolean matches(ItemStack stack) {
        EcoItem item = ItemUtilsKt.getEcoItem(stack);
        return item != null && item.getID().equals(this.itemId);
    }
}
