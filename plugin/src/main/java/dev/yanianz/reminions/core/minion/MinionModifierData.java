package dev.yanianz.reminions.core.minion;

import java.util.UUID;
import dev.yanianz.reminions.core.modifier.ModifierType;

public class MinionModifierData {
    private final UUID id;
    private final String name;
    private final ModifierType type;
    private final int slot;
    private final long appliedAt;
    private final long duration;
    private long soldItems;
    private double moneyEarned;

    public MinionModifierData(UUID id, String name, ModifierType type, int slot, long appliedAt, long duration) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.slot = slot;
        this.appliedAt = appliedAt;
        this.duration = duration;
    }

    public MinionModifierData(UUID id, String name, ModifierType type, int slot,
                              long appliedAt, long duration, long soldItems, double moneyEarned) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.slot = slot;
        this.appliedAt = appliedAt;
        this.duration = duration;
        this.soldItems = soldItems;
        this.moneyEarned = moneyEarned;
    }

    public void addSoldItems(long delta) {
        this.soldItems += delta;
    }

    public void addMoneyEarned(double delta) {
        this.moneyEarned += delta;
    }

    public boolean isExpired() {
        return this.duration >= 0L && System.currentTimeMillis() - this.appliedAt >= this.duration;
    }

    public int durationLeft() {
        if (this.duration < 0L) return -1;
        long now = System.currentTimeMillis();
        long remaining = this.appliedAt + this.duration - now;
        return remaining <= 0L ? 0 : (int) (remaining / 1000L);
    }

    public UUID getId()          { return this.id; }
    public String getName()      { return this.name; }
    public ModifierType getType(){ return this.type; }
    public int getSlot()         { return this.slot; }
    public long getAppliedAt()   { return this.appliedAt; }
    public long getDuration()    { return this.duration; }
    public long getSoldItems()   { return this.soldItems; }
    public double getMoneyEarned(){ return this.moneyEarned; }

    public void setSoldItems(long value)    { this.soldItems = value; }
    public void setMoneyEarned(double value){ this.moneyEarned = value; }
}
