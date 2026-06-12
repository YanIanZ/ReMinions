package dev.yanianz.reminions.database.impl;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.database.Database;
import dev.yanianz.reminions.database.DatabaseMeta;

/** Write-through cache: reads prefer Redis, falls back to MySQL; writes go to both. */
public class CachedDatabase extends Database {

    private final MySQLDatabase mysql = new MySQLDatabase();
    private final RedisDatabase redis = new RedisDatabase();

    @Override
    public void connect(DatabaseMeta meta) {
        this.mysql.connect(meta);
        this.redis.connect(meta);
    }

    @Override
    public void disconnect() {
        this.mysql.disconnect();
        this.redis.disconnect();
    }

    @Override
    public void createTables() {
        this.mysql.createTables();
    }

    @Override
    public PlayerMinions getPlayerMinions(UUID playerId) {
        PlayerMinions cached = this.redis.getPlayerMinions(playerId);
        if (cached != null) return cached;
        PlayerMinions fromDb = this.mysql.getPlayerMinions(playerId);
        if (fromDb != null) this.redis.savePlayerMinions(fromDb);
        return fromDb;
    }

    @Override
    public List<PlayerMinions> getPlayerMinions() {
        return this.mysql.getPlayerMinions();
    }

    @Override
    public boolean savePlayerMinions(PlayerMinions player) {
        this.redis.savePlayerMinions(player);
        return this.mysql.savePlayerMinions(player);
    }

    @Override
    public boolean savePlayersMinions(Collection<PlayerMinions> players) {
        boolean success = this.mysql.savePlayersMinions(players);
        if (success) {
            for (PlayerMinions player : players) this.redis.savePlayerMinions(player);
        }
        return success;
    }
}
