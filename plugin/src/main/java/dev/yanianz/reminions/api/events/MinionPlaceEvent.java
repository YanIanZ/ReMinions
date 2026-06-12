package dev.yanianz.reminions.api.events;

import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionMeta;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MinionPlaceEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final Location location;
    private final MinionMeta meta;
    @Nullable private Minion minion;
    private boolean cancelled;

    public MinionPlaceEvent(Player player, Location location, MinionMeta meta) {
        this.player = player;
        this.location = location;
        this.meta = meta;
    }

    public Player getPlayer()   { return this.player; }
    public Location getLocation(){ return this.location; }
    public MinionMeta getMeta() { return this.meta; }
    public Minion getMinion()   { return this.minion; }
    public void setMinion(Minion minion) { this.minion = minion; }

    @NotNull public HandlerList getHandlers()           { return handlers; }
    public static HandlerList getHandlerList()          { return handlers; }

    public boolean isCancelled()              { return this.cancelled; }
    public void setCancelled(boolean value)   { this.cancelled = value; }
}
