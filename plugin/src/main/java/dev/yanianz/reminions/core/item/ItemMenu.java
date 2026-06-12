package dev.yanianz.reminions.core.item;

import java.util.Arrays;

public class ItemMenu extends ItemBuilder {
    private final int[] slots;

    public ItemMenu(ItemBuilder source, int[] slots) {
        super(source);
        this.slots = slots;
    }

    public int[] getSlots() { return this.slots; }

    @Override
    public String toString() {
        return "ItemMenu(slots=" + Arrays.toString(this.getSlots()) + ")";
    }
}
