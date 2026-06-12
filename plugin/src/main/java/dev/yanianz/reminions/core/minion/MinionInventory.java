package dev.yanianz.reminions.core.minion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Thread-safe virtual inventory for a minion.
 * Items are stored as {@link ItemData} entries; slot capacity is tracked by max-stack-size boundaries.
 */
public class MinionInventory {

    private final List<ItemData> items;
    private int maxSlots;
    private long collected;

    public MinionInventory(List<ItemData> items, long collected) {
        this.items = Collections.synchronizedList(items);
        this.collected = collected;
    }

    public MinionInventory(List<ItemData> items, long collected, int maxSlots) {
        this.items = Collections.synchronizedList(items);
        this.collected = collected;
        this.maxSlots = maxSlots;
    }

    public void addCollected(int amount) {
        this.collected += amount;
    }

    /**
     * Attempts to add all supplied {@code entries} into the inventory.
     * Returns a list of the amounts actually accepted (may be less than requested if inventory is full).
     */
    public List<ItemData> addItems(ItemData... entries) {
        List<ItemData> accepted = new ArrayList<>();
        if (entries == null || entries.length == 0) return accepted;

        synchronized (this.items) {
            int occupiedSlots = this.items.stream()
                    .mapToInt(d -> d.splitIntoStacks(d.getItem().getMaxStackSize()).size())
                    .sum();

            for (ItemData entry : entries) {
                int remaining = entry.getAmount();
                int stackSize = entry.getItem().getMaxStackSize();
                ItemData existingData = null;

                // Fill partial stacks of matching items first.
                for (ItemData slot : this.items) {
                    if (!slot.getItem().isSimilar(entry.getItem())) continue;
                    existingData = slot;
                    int partialRoom = stackSize - slot.getAmount() % stackSize;
                    if (partialRoom == stackSize) continue; // already full stack
                    int toFill = Math.min(partialRoom, remaining);
                    slot.sum(toFill);
                    this.collected += toFill;
                    remaining -= toFill;
                    if (toFill > 0) accepted.add(ItemData.of(slot.getItem(), toFill));
                    if (remaining <= 0) break;
                }

                if (remaining <= 0) continue;

                // Need new slot(s).
                int newSlotsNeeded = (int) Math.ceil((double) remaining / stackSize);
                if (occupiedSlots + newSlotsNeeded > this.maxSlots) {
                    // Partially fill up to available capacity.
                    int freeSlots = this.maxSlots - occupiedSlots;
                    int canAccept = freeSlots * stackSize;
                    if (canAccept > 0) {
                        int toAdd = Math.min(remaining, canAccept);
                        if (existingData != null) {
                            existingData.sum(toAdd);
                        } else {
                            this.items.add(new ItemData(entry.getItem(), toAdd));
                        }
                        this.collected += toAdd;
                        accepted.add(ItemData.of(entry.getItem(), toAdd));
                    }
                    return accepted;
                }

                if (existingData != null) {
                    existingData.sum(remaining);
                } else {
                    this.items.add(new ItemData(entry.getItem(), remaining));
                }
                this.collected += remaining;
                accepted.add(ItemData.of(entry.getItem(), remaining));
                occupiedSlots += newSlotsNeeded;
            }
        }
        return accepted;
    }

    /** Removes up to {@code target.amount} of a matching item. Pass amount {@code -1} to remove all. */
    public ItemData removeItem(ItemData target) {
        int toRemove = target.amount;
        synchronized (this.items) {
            Iterator<ItemData> iter = this.items.iterator();
            while (iter.hasNext()) {
                ItemData slot = iter.next();
                if (!slot.getItem().isSimilar(target.getItem())) continue;
                if (toRemove != -1 && toRemove < slot.getAmount()) {
                    slot.rest(toRemove);
                    return ItemData.of(new ItemStack(slot.getItem()), toRemove);
                }
                iter.remove();
                return ItemData.of(new ItemStack(slot.getItem()), slot.getAmount());
            }
            return null;
        }
    }

    /** Removes up to {@code amount} units of items similar to {@code template}. Returns the count actually removed. */
    public int removeSimilar(ItemStack template, int amount) {
        if (template == null || amount <= 0) return 0;
        int remaining = amount;
        int removed = 0;
        synchronized (this.items) {
            Iterator<ItemData> iter = this.items.iterator();
            while (iter.hasNext() && remaining > 0) {
                ItemData slot = iter.next();
                if (!slot.getItem().isSimilar(template)) continue;
                int take = Math.min(slot.getAmount(), remaining);
                slot.rest(take);
                remaining -= take;
                removed += take;
                if (slot.getAmount() <= 0) iter.remove();
            }
        }
        return removed;
    }

    /** Returns an immutable snapshot of all current item data. */
    public List<ItemData> getSnapshot() {
        synchronized (this.items) {
            return this.items.stream()
                    .map(d -> ItemData.of(d.id, new ItemStack(d.getItem()), d.getAmount()))
                    .toList();
        }
    }

    public boolean hasItem(ItemData query) {
        if (query == null) return false;
        synchronized (this.items) {
            for (ItemData slot : this.items) {
                if (slot.getItem().isSimilar(query.getItem())
                        && (query.getAmount() == -1 || slot.getAmount() >= query.getAmount())) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public int countItem(ItemStack template) {
        if (template == null) return 0;
        synchronized (this.items) {
            int count = 0;
            for (ItemData slot : this.items) {
                if (slot.getItem().isSimilar(template)) count += slot.getAmount();
            }
            return count;
        }
    }

    public boolean isFull() {
        synchronized (this.items) {
            int usedSlots = this.items.stream()
                    .mapToInt(d -> d.splitIntoStacks(d.getItem().getMaxStackSize()).size())
                    .sum();
            if (usedSlots < this.maxSlots) return false;
            for (ItemData slot : this.items) {
                int maxStack = slot.getItem().getMaxStackSize();
                for (ItemStack stack : slot.splitIntoStacks(maxStack)) {
                    if (stack.getAmount() < maxStack) return false;
                }
            }
            return true;
        }
    }

    public ItemData getItemData(UUID id) {
        synchronized (this.items) {
            return this.items.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
        }
    }

    @Override
    public String toString() {
        return "MinionInventory(items=" + this.items + ", maxSlots=" + this.maxSlots + ", collected=" + this.collected + ")";
    }

    public int getMaxSlots()             { return this.maxSlots; }
    public void setMaxSlots(int slots)   { this.maxSlots = slots; }
    public long getCollected()           { return this.collected; }
    public void setCollected(long value) { this.collected = value; }

    // ─────────────────────────────────────────────────────────────────────────────
    // ItemData
    // ─────────────────────────────────────────────────────────────────────────────

    public static class ItemData {

        private final UUID id;
        private final ItemStack item;
        private int amount;

        protected ItemData(ItemStack item, int amount) {
            this.id = UUID.randomUUID();
            this.item = item;
            this.item.setAmount(1);
            this.amount = amount;
        }

        protected ItemData(UUID id, ItemStack item, int amount) {
            this.id = id;
            this.item = item;
            this.item.setAmount(1);
            this.amount = amount;
        }

        public void sum(int delta)  { this.amount += delta; }
        public void rest(int delta) { this.amount -= delta; }

        public static ItemData of(ItemStack item, int amount) {
            return new ItemData(item, amount);
        }

        public static ItemData of(UUID id, ItemStack item, int amount) {
            return new ItemData(id, item, amount);
        }

        @NotNull
        public List<ItemStack> splitIntoStacks(int stackSize) {
            List<ItemStack> stacks = new ArrayList<>();
            int remaining = this.amount;
            while (remaining > 0) {
                int batch = Math.min(stackSize, remaining);
                ItemStack stack = new ItemStack(this.item);
                stack.setAmount(batch);
                stacks.add(stack);
                remaining -= batch;
            }
            return stacks;
        }

        public UUID getId()             { return this.id; }
        public ItemStack getItem()      { return this.item; }
        public int getAmount()          { return this.amount; }
        public void setAmount(int amt)  { this.amount = amt; }

        @Override
        public String toString() {
            return "MinionInventory.ItemData(id=" + this.id + ", item=" + this.item + ", amount=" + this.amount + ")";
        }
    }
}
