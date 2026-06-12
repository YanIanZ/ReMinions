package dev.yanianz.reminions.config;

import java.util.HashMap;
import java.util.Map;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.minion.MinionStatus;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class MinionSkinConfig {
    private final String category;
    private final Map<EquipmentSlot, ItemBuilder> slots = new HashMap<>();
    private final Map<MinionStatus, String[]> holograms;

    public MinionSkinConfig(String category, Map<MinionStatus, String[]> holograms) {
        this.category = category;
        this.holograms = holograms;
    }

    public void addSlot(EquipmentSlot slot, ItemBuilder builder) {
        this.slots.put(slot, builder);
    }

    public ItemBuilder getSlot(EquipmentSlot slot) {
        return this.slots.get(slot);
    }

    public ItemStack getHead() {
        ItemBuilder builder = this.slots.getOrDefault(EquipmentSlot.HEAD, null);
        return builder == null ? null : builder.toBuild();
    }

    public ItemStack getChest() {
        ItemBuilder builder = this.slots.getOrDefault(EquipmentSlot.CHEST, null);
        return builder == null ? null : builder.toBuild();
    }

    public ItemStack getLegs() {
        ItemBuilder builder = this.slots.getOrDefault(EquipmentSlot.LEGS, null);
        return builder == null ? null : builder.toBuild();
    }

    public ItemStack getFeet() {
        ItemBuilder builder = this.slots.getOrDefault(EquipmentSlot.FEET, null);
        return builder == null ? null : builder.toBuild();
    }

    public ItemStack getHand() {
        ItemBuilder builder = this.slots.getOrDefault(EquipmentSlot.HAND, null);
        return builder == null ? null : builder.toBuild();
    }

    public ItemStack getOffHand() {
        ItemBuilder builder = this.slots.getOrDefault(EquipmentSlot.OFF_HAND, null);
        return builder == null ? null : builder.toBuild();
    }

    public String getCategory()                        { return this.category; }
    public Map<EquipmentSlot, ItemBuilder> getSlots()  { return this.slots; }
    public Map<MinionStatus, String[]> getHolograms()  { return this.holograms; }
}
