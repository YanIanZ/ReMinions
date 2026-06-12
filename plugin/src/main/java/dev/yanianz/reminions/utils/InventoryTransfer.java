package dev.yanianz.reminions.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import dev.yanianz.reminions.core.minion.MinionInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryTransfer {
    private static final int PLAYER_STORAGE_SIZE = 36;

    private InventoryTransfer() {}

    public static TransferResult collect(PlayerInventory playerInv, MinionInventory minionInv) {
        if (playerInv == null || minionInv == null || minionInv.isEmpty()) return TransferResult.empty();
        int totalMoved = 0;
        List<MinionInventory.ItemData> removed = new ArrayList<>();

        for (MinionInventory.ItemData slot : minionInv.getSnapshot()) {
            ItemStack item = slot.getItem();
            int available = slot.getAmount();
            int canFit = countFitAmount(playerInv, item, available);
            if (canFit <= 0) break;

            MinionInventory.ItemData taken = minionInv.removeItem(MinionInventory.ItemData.of(item, canFit));
            if (taken == null) break;

            ItemStack toGive = taken.getItem().clone();
            toGive.setAmount(taken.getAmount());
            if (!addAll(playerInv, List.of(toGive))) {
                minionInv.addItems(taken);
                break;
            }

            totalMoved += taken.getAmount();
            removed.add(taken);
            if (canFit < available) break;
        }

        return new TransferResult(totalMoved, removed);
    }

    public static boolean transferExact(PlayerInventory playerInv, MinionInventory minionInv,
                                        MinionInventory.ItemData itemData, int amount) {
        if (playerInv == null || minionInv == null || itemData == null || amount <= 0) return false;
        ItemStack toGive = itemData.getItem().clone();
        toGive.setAmount(amount);
        if (!canFit(playerInv, List.of(toGive))) return false;

        MinionInventory.ItemData taken = minionInv.removeItem(MinionInventory.ItemData.of(itemData.getItem(), amount));
        if (taken == null || taken.getAmount() != amount) {
            if (taken != null) minionInv.addItems(taken);
            return false;
        }

        if (!addAll(playerInv, List.of(toGive))) {
            minionInv.addItems(taken);
            return false;
        }
        return true;
    }

    public static boolean canFit(PlayerInventory playerInv, Collection<ItemStack> items) {
        if (playerInv == null) return false;
        ItemStack[] simulated = new ItemStack[PLAYER_STORAGE_SIZE];
        for (int i = 0; i < PLAYER_STORAGE_SIZE; i++) {
            ItemStack slot = playerInv.getItem(i);
            simulated[i] = isEmpty(slot) ? null : slot.clone();
        }

        for (ItemStack item : items) {
            if (isEmpty(item)) continue;
            int remaining = item.getAmount();
            int maxStack = Math.max(1, item.getMaxStackSize());

            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                ItemStack slot = simulated[i];
                if (!isEmpty(slot) && slot.isSimilar(item)) {
                    int space = Math.max(0, maxStack - slot.getAmount());
                    if (space > 0) {
                        int moved = Math.min(remaining, space);
                        slot.setAmount(slot.getAmount() + moved);
                        remaining -= moved;
                    }
                }
            }
            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                if (isEmpty(simulated[i])) {
                    int moved = Math.min(remaining, maxStack);
                    ItemStack newSlot = item.clone();
                    newSlot.setAmount(moved);
                    simulated[i] = newSlot;
                    remaining -= moved;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    public static boolean addAll(PlayerInventory playerInv, Collection<ItemStack> items) {
        if (!canFit(playerInv, items)) return false;
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                HashMap<Integer, ItemStack> leftovers = playerInv.addItem(item.clone());
                if (!leftovers.isEmpty()) return false;
            }
        }
        return true;
    }

    public static List<ItemStack> flatten(MinionInventory minionInv) {
        List<ItemStack> result = new ArrayList<>();
        if (minionInv == null || minionInv.isEmpty()) return result;
        for (MinionInventory.ItemData slot : minionInv.getSnapshot()) {
            result.addAll(slot.splitIntoStacks(slot.getItem().getMaxStackSize()));
        }
        return result;
    }

    public static List<ItemStack> flattenWithExtra(MinionInventory minionInv, ItemStack extra) {
        List<ItemStack> result = flatten(minionInv);
        if (!isEmpty(extra)) result.add(extra.clone());
        return result;
    }

    private static int countFitAmount(PlayerInventory playerInv, ItemStack item, int requested) {
        if (playerInv == null || isEmpty(item) || requested <= 0) return 0;
        int remaining = requested;
        int maxStack = Math.max(1, item.getMaxStackSize());

        for (int i = 0; i < PLAYER_STORAGE_SIZE && remaining > 0; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (!isEmpty(slot) && slot.isSimilar(item)) {
                int space = Math.max(0, maxStack - slot.getAmount());
                remaining -= Math.min(remaining, space);
            }
        }
        for (int i = 0; i < PLAYER_STORAGE_SIZE && remaining > 0; i++) {
            if (isEmpty(playerInv.getItem(i))) remaining -= Math.min(remaining, maxStack);
        }
        return requested - Math.max(0, remaining);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    public record TransferResult(int totalMoved, List<MinionInventory.ItemData> removedItems) {
        private static TransferResult empty() {
            return new TransferResult(0, List.of());
        }

        public boolean movedAny() {
            return this.totalMoved > 0;
        }
    }
}
