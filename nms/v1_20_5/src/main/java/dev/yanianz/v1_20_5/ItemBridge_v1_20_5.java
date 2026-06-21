package dev.yanianz.v1_20_5;

import dev.yanianz.reminions.nms.ApiBackedNmsAdapter;
import dev.yanianz.reminions.nms.ItemBridge;
import org.bukkit.enchantments.Enchantment;

/** Item bridge for 1.20.5 – 1.20.6. Uses the shared registry-based enchantment resolver. */
public final class ItemBridge_v1_20_5 implements ItemBridge {
    @Override
    public Enchantment applyEnchant(String enchantName) {
        return ApiBackedNmsAdapter.ITEM_BRIDGE.applyEnchant(enchantName);
    }
}
