package dev.yanianz.reminions.database.impl;

import com.google.gson.reflect.TypeToken;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.ReplaceOneModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.database.Database;
import dev.yanianz.reminions.database.DatabaseMeta;
import dev.yanianz.reminions.utils.DebugLogger;
import org.bson.Document;

/**
 * MongoDB-backed persistence. Stores one document per player with the same fields shape
 * as the SQL schema (name + serialized minions JSON + max_minions + unique_minions).
 * Minions and unique_minions remain Gson-serialized JSON strings so the existing adapter
 * stack (handles ItemStack/Location3f/etc.) does not need to learn about BSON.
 */
public class MongoDatabase extends Database {

    private static final String COLLECTION_NAME = "players";

    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private MongoClient client;
    private com.mongodb.client.MongoDatabase db;
    private MongoCollection<Document> collection;

    @Override
    public void connect(DatabaseMeta meta) {
        if (this.client != null) return;
        try {
            String uri = meta.getMongoUri();
            MongoClientSettings.Builder builder = MongoClientSettings.builder();
            if (uri != null && !uri.isEmpty()) {
                builder.applyConnectionString(new com.mongodb.ConnectionString(uri));
            } else {
                String user = meta.getUser();
                String pass = meta.getPassword();
                String auth = (user != null && !user.isEmpty())
                        ? user + ":" + pass + "@"
                        : "";
                String fallbackUri = "mongodb://" + auth + meta.getHost() + ":" + meta.getPort();
                builder.applyConnectionString(new com.mongodb.ConnectionString(fallbackUri));
            }
            this.client = MongoClients.create(builder.build());
            this.db = this.client.getDatabase(meta.getDatabaseName());
            this.collection = this.db.getCollection(COLLECTION_NAME);
            this.createTables();
        } catch (Exception e) {
            DebugLogger.warn("[Mongo] Failed to connect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void disconnect() {
        if (this.client != null) this.client.close();
    }

    @Override
    public void createTables() {
        // Mongo is schemaless; ensuring the collection exists is enough. The driver creates
        // it lazily on first write, but we touch it here so admins see it on first connect.
        if (this.db != null && this.collection == null) {
            this.collection = this.db.getCollection(COLLECTION_NAME);
        }
    }

    @Override
    public PlayerMinions getPlayerMinions(UUID playerId) {
        if (this.collection == null) return null;
        try {
            Document doc = this.collection.find(Filters.eq("_id", playerId.toString())).first();
            return doc == null ? null : this.fromDocument(playerId, doc);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<PlayerMinions> getPlayerMinions() {
        List<PlayerMinions> result = new ArrayList<>();
        if (this.collection == null) return result;
        try {
            for (Document doc : this.collection.find()) {
                UUID id = UUID.fromString(doc.getString("_id"));
                result.add(this.fromDocument(id, doc));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public boolean savePlayersMinions(Collection<PlayerMinions> players) {
        if (this.collection == null || players.isEmpty()) return true;
        try {
            List<WriteModel<Document>> ops = new ArrayList<>(players.size());
            for (PlayerMinions p : players) {
                ops.add(new ReplaceOneModel<>(
                        Filters.eq("_id", p.getOwner().toString()),
                        this.toDocument(p),
                        UPSERT));
            }
            this.collection.bulkWrite(ops);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean savePlayerMinions(PlayerMinions player) {
        if (this.collection == null) return false;
        try {
            this.collection.replaceOne(
                    Filters.eq("_id", player.getOwner().toString()),
                    this.toDocument(player),
                    UPSERT);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Document toDocument(PlayerMinions player) {
        String minionsJson = GSON.toJson(new ArrayList<>(player.getMinions()), new TypeToken<List<Minion>>(){}.getType());
        String uniqueJson = GSON.toJson(player.getMinionUnlockeds(), new TypeToken<Map<String, Integer>>(){}.getType());
        return new Document("_id", player.getOwner().toString())
                .append("name", player.getOwnerName())
                .append("minions", minionsJson)
                .append("max_minions", player.getMaxMinions())
                .append("unique_minions", uniqueJson);
    }

    private PlayerMinions fromDocument(UUID id, Document doc) {
        String name = doc.getString("name");
        String minionsJson = doc.getString("minions");
        int maxMinions = doc.getInteger("max_minions", 0);
        String uniqueJson = doc.getString("unique_minions");

        PlayerMinions player = new PlayerMinions(id, name);
        if (minionsJson != null && !minionsJson.isEmpty()) {
            List<Minion> minions = GSON.fromJson(minionsJson, new TypeToken<List<Minion>>(){}.getType());
            player.addMinions(minions);
        }
        Map<String, Integer> unlocked = new HashMap<>();
        if (uniqueJson != null && !uniqueJson.isEmpty()) {
            unlocked = GSON.fromJson(uniqueJson, new TypeToken<Map<String, Integer>>(){}.getType());
        }
        player.getMinionUnlockeds().putAll(unlocked);
        player.setMaxMinions(maxMinions);
        return player;
    }
}
