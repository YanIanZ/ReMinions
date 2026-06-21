package dev.yanianz.v26_1_2;

import dev.yanianz.reminions.nms.ItemBridge;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

public class ItemBridge_v26_1_2 implements ItemBridge {
    private static final Registry<Enchantment> ENCHANTMENT_REGISTRY =
            RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

    @Override
    public Enchantment applyEnchant(String name) {
        return ENCHANTMENT_REGISTRY.get(NamespacedKey.minecraft(name));
    }
}
