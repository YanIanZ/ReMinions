package dev.yanianz.reminions.config;

import java.util.List;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.modifier.ModifierCategory;
import dev.yanianz.reminions.core.modifier.ModifierType;
import dev.yanianz.reminions.core.product.Product;

public record ModifierConfig(
   String id,
   String displayName,
   ModifierCategory category,
   ModifierType type,
   double value,
   List<Product> upgradeProducts,
   ItemBuilder item,
   int duration,
   boolean unbreakable
) {
}
