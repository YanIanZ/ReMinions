package dev.yanianz.reminions.api.events;

import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class MinionItemsProduceEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Minion minion;
    private final MinionInventory inventory;
    private final MinionInventory.ItemData[] items;
    private final ResultState result;

    public MinionItemsProduceEvent(Minion minion, MinionInventory inventory,
                                   MinionInventory.ItemData[] items, ResultState result) {
        this.minion = minion;
        this.inventory = inventory;
        this.items = items;
        this.result = result;
    }

    public Minion getMinion()                  { return this.minion; }
    public MinionInventory getInventory()      { return this.inventory; }
    public MinionInventory.ItemData[] getItems(){ return this.items; }
    public ResultState getResult()             { return this.result; }

    @NotNull public static HandlerList getHandlerList() { return handlers; }
    @NotNull public HandlerList getHandlers()           { return handlers; }

    public enum ResultState { SUCESS, FAILED }
}
