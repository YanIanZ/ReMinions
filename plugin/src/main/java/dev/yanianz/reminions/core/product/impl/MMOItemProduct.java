package dev.yanianz.reminions.core.product.impl;

import dev.yanianz.reminions.config.SourceExpConfig;
import dev.yanianz.reminions.core.product.Product;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.inventory.ItemStack;

public class MMOItemProduct extends Product {
    private final String itemId;
    private final String itemCategory;

    public MMOItemProduct(String id, double price, int amountStart, int amountEnd,
                          double chance, String requiredProduct, int requiredAmount,
                          SourceExpConfig expConfig, String itemId, String itemCategory) {
        super(id, price, amountStart, amountEnd, chance, requiredProduct, requiredAmount, expConfig);
        this.itemId = itemId;
        this.itemCategory = itemCategory;
    }

    @Override
    public ItemStack buildItem() {
        Type type = Type.get(this.itemCategory);
        if (type == null) return null;
        MMOItem mmoItem = MMOItems.plugin.getMMOItem(type, this.itemId);
        return mmoItem == null ? null : mmoItem.newBuilder().build();
    }

    @Override
    public boolean matches(ItemStack stack) {
        Type type = MMOItems.getType(stack);
        String id = MMOItems.getID(stack);
        return type != null && id != null && type.getId().equals(this.itemCategory) && id.equals(this.itemId);
    }
}
