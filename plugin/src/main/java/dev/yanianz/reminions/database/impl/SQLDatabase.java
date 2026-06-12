package dev.yanianz.reminions.database.impl;

import com.google.gson.reflect.TypeToken;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

public class SQLDatabase extends Database {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS players (
                id CHAR(36) PRIMARY KEY,
                name VARCHAR(32) NOT NULL,
                minions TEXT NOT NULL,
                max_minions INT,
                unique_minions TEXT
            )
            """;

    private static final String SELECT_ONE_SQL =
            "SELECT name, minions, max_minions, unique_minions FROM players WHERE id = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT id, name, minions, max_minions, unique_minions FROM players";

    private static final String UPSERT_SQL = """
            INSERT INTO players (id, name, minions, max_minions, unique_minions)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                minions = excluded.minions,
                max_minions = excluded.max_minions,
                unique_minions = excluded.unique_minions
            """;

    private Connection connection;

    @Override
    public void connect(DatabaseMeta meta) {
        try {
            if (this.connection != null && !this.connection.isClosed()) return;
            this.connection = DriverManager.getConnection(
                    "jdbc:sqlite:plugins/ReMinions/" + meta.getFileName());
            this.createTables();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void disconnect() {
        try {
            if (this.connection != null && !this.connection.isClosed()) this.connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void createTables() {
        try (Statement stmt = this.connection.createStatement()) {
            stmt.executeUpdate(CREATE_TABLE_SQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public PlayerMinions getPlayerMinions(UUID playerId) {
        try (PreparedStatement stmt = this.connection.prepareStatement(SELECT_ONE_SQL)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                String name        = rs.getString("name");
                String minionsJson = rs.getString("minions");
                int maxMinions     = rs.getInt("max_minions");
                Map<String, Integer> unlocked = GSON.fromJson(
                        rs.getString("unique_minions"), new TypeToken<Map<String, Integer>>(){}.getType());

                PlayerMinions player = new PlayerMinions(playerId, name);
                List<Minion> minions = GSON.fromJson(minionsJson, new TypeToken<List<Minion>>(){}.getType());
                player.addMinions(minions);
                player.getMinionUnlockeds().putAll(unlocked);
                player.setMaxMinions(maxMinions);
                return player;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<PlayerMinions> getPlayerMinions() {
        List<PlayerMinions> result = new ArrayList<>();
        try (PreparedStatement stmt = this.connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID playerId    = UUID.fromString(rs.getString("id"));
                String name      = rs.getString("name");
                String minionsJson = rs.getString("minions");
                int maxMinions   = rs.getInt("max_minions");
                String uniqueJson = rs.getString("unique_minions");

                Map<String, Integer> unlocked = new HashMap<>();
                if (uniqueJson != null && !uniqueJson.isEmpty()) {
                    unlocked = GSON.fromJson(uniqueJson, new TypeToken<Map<String, Integer>>(){}.getType());
                }

                PlayerMinions player = new PlayerMinions(playerId, name);
                player.setMaxMinions(maxMinions);
                player.getMinionUnlockeds().putAll(unlocked);
                if (minionsJson != null && !minionsJson.isEmpty()) {
                    List<Minion> minions = GSON.fromJson(minionsJson, new TypeToken<List<Minion>>(){}.getType());
                    player.addMinions(minions);
                }
                result.add(player);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public boolean savePlayersMinions(Collection<PlayerMinions> players) {
        boolean allSucceeded = true;
        for (PlayerMinions player : players) {
            if (!savePlayerMinions(player)) allSucceeded = false;
        }
        return allSucceeded;
    }

    @Override
    public boolean savePlayerMinions(PlayerMinions player) {
        try (PreparedStatement stmt = this.connection.prepareStatement(UPSERT_SQL)) {
            stmt.setString(1, player.getOwner().toString());
            stmt.setString(2, player.getOwnerName());
            String minionsJson = GSON.toJson(new ArrayList<>(player.getMinions()), new TypeToken<List<Minion>>(){}.getType());
            stmt.setString(3, minionsJson);
            stmt.setInt(4, player.getMaxMinions());
            stmt.setString(5, GSON.toJson(player.getMinionUnlockeds(), new TypeToken<Map<String, Integer>>(){}.getType()));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
