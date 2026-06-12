package dev.yanianz.reminions.api.events;

import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.product.Product;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class MinionSellItemsEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Minion minion;
    private final Product itemSell;
    private final double profit;
    private final int amountSelling;
    private boolean cancelled;

    public MinionSellItemsEvent(Minion minion, Product itemSell, double profit, int amountSelling) {
        this.minion = minion;
        this.itemSell = itemSell;
        this.profit = profit;
        this.amountSelling = amountSelling;
    }

    public Minion getMinion()     { return this.minion; }
    public Product getItemSell()  { return this.itemSell; }
    public double getProfit()     { return this.profit; }
    public int getAmountSelling() { return this.amountSelling; }

    @NotNull public HandlerList getHandlers()   { return handlers; }
    public static HandlerList getHandlerList()  { return handlers; }

    public boolean isCancelled()              { return this.cancelled; }
    public void setCancelled(boolean value)   { this.cancelled = value; }
}
