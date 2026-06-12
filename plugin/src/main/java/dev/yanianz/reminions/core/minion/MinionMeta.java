package dev.yanianz.reminions.core.minion;

public class MinionMeta {
    private final String id;
    private final int level;
    private final long collected;
    private final long lastGenerated;

    public MinionMeta(String id, int level) {
        this.id = id;
        this.level = level;
        this.lastGenerated = 0L;
        this.collected = 0L;
    }

    public MinionMeta(Minion minion) {
        this.id = minion.getName();
        this.level = minion.getLevel();
        this.collected = minion.getCollected();
        this.lastGenerated = minion.getLastGenerated();
    }

    public String getId()         { return this.id; }
    public int getLevel()         { return this.level; }
    public long getCollected()    { return this.collected; }
    public long getLastGenerated(){ return this.lastGenerated; }
}
