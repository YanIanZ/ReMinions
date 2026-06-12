package dev.yanianz.v1_20_4;

import dev.yanianz.reminions.nms.ItemBridge;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

public class ItemBrigde_v1_20_4 implements ItemBridge {
   @Override
   public Enchantment applyEnchant(String enchName) {
      return Enchantment.getByKey(NamespacedKey.minecraft(enchName));
   }
}
