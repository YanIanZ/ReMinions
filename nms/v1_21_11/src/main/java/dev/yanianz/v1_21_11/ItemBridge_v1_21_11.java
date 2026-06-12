package dev.yanianz.v1_21_11;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import dev.yanianz.reminions.nms.ItemBridge;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

public class ItemBridge_v1_21_11 implements ItemBridge {
   private static final Registry<Enchantment> ENCHANTMENT_REGISTRY = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

   @Override
   public Enchantment applyEnchant(String var1) {
      return (Enchantment)ENCHANTMENT_REGISTRY.get(NamespacedKey.minecraft(var1));
   }
}
