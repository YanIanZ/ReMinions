package dev.yanianz.reminions.database.impl;

import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.database.Database;
import dev.yanianz.reminions.database.DatabaseMeta;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

public class RedisDatabase extends Database {
    private JedisPool jedisPool;

    private static final String KEY_PREFIX = "beeminions:player:";
    private static final int TTL_SECONDS   = 600;

    @Override
    public void connect(DatabaseMeta meta) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(true);
        this.jedisPool = new JedisPool(poolConfig, meta.getRedisHost(), meta.getRedisPort(), 2000, meta.getRedisPassword());
    }

    @Override
    public void disconnect() {
        if (this.jedisPool != null && !this.jedisPool.isClosed()) this.jedisPool.close();
    }

    private Jedis getResource() {
        return this.jedisPool.getResource();
    }

    @Override
    public void createTables() {}

    @Override
    public PlayerMinions getPlayerMinions(UUID playerId) {
        try (Jedis jedis = this.getResource()) {
            String json = jedis.get(KEY_PREFIX + playerId);
            if (json == null) return null;
            return GSON.fromJson(json, PlayerMinions.class);
        }
    }

    @Override
    public List<PlayerMinions> getPlayerMinions() {
        List<PlayerMinions> result = new ArrayList<>();
        try (Jedis jedis = this.getResource()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams scanParams = new ScanParams().match(KEY_PREFIX + "*").count(200);
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                List<String> keys = scanResult.getResult();
                cursor = scanResult.getCursor();
                if (!keys.isEmpty()) {
                    Pipeline pipe = jedis.pipelined();
                    for (String key : keys) pipe.get(key);
                    for (Object raw : pipe.syncAndReturnAll()) {
                        if (raw == null) continue;
                        String json = (String) raw;
                        if (json.isEmpty()) continue;
                        PlayerMinions player = GSON.fromJson(json, PlayerMinions.class);
                        if (player != null) result.add(player);
                    }
                }
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public boolean savePlayersMinions(Collection<PlayerMinions> players) {
        boolean success = true;
        for (PlayerMinions player : players) {
            if (!this.savePlayerMinions(player)) success = false;
        }
        return success;
    }

    @Override
    public boolean savePlayerMinions(PlayerMinions player) {
        try (Jedis jedis = this.getResource()) {
            String json = GSON.toJson(player, new TypeToken<PlayerMinions>() {}.getType());
            jedis.setex(KEY_PREFIX + player.getOwner(), TTL_SECONDS, json);
            return true;
        }
    }
}
