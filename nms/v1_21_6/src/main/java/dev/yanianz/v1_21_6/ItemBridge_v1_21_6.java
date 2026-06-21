package dev.yanianz.v1_21_6;

import dev.yanianz.reminions.nms.ApiBackedNmsAdapter;
import dev.yanianz.reminions.nms.ItemBridge;
import org.bukkit.enchantments.Enchantment;

/** Item bridge for 1.21.6 – 1.21.7. Uses the shared registry-based enchantment resolver. */
public final class ItemBridge_v1_21_6 implements ItemBridge {
    @Override
    public Enchantment applyEnchant(String enchantName) {
        return ApiBackedNmsAdapter.ITEM_BRIDGE.applyEnchant(enchantName);
    }
}
