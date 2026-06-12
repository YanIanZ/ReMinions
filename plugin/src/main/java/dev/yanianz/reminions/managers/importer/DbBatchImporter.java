package dev.yanianz.reminions.managers.importer;

import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.core.item.ItemBuilder;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionStatus;
import dev.yanianz.reminions.core.minion.MinionType;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.database.Database;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.managers.importer.old.MinionOld;
import dev.yanianz.reminions.utils.DebugLogger;
import dev.yanianz.reminions.utils.Location3f;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.dev.yanianz.reminions.BeeMinions;
import org.dev.yanianz.reminions.data.minion.template.MinionTemplate;

public class DbBatchImporter {
    private final File importFolder;
    private final MinionManager minionManager;
    private final Database database;

    public DbBatchImporter(File importFolder, MinionManager minionManager, Database database) {
        this.importFolder = importFolder;
        this.minionManager = minionManager;
        this.database = database;
    }

    public void importAll() {
        if (!this.importFolder.exists()) {
            throw new IllegalStateException("Import folder does not exist: " + this.importFolder.getAbsolutePath());
        }
        DebugLogger.info("[Importer] Starting batch import from: " + this.importFolder.getAbsolutePath());
        File[] dbFiles = this.importFolder.listFiles(
                (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".db"));
        if (dbFiles == null || dbFiles.length == 0) {
            DebugLogger.warn("[Importer] No .db files found in: " + this.importFolder.getAbsolutePath());
            return;
        }
        for (File dbFile : dbFiles) {
            try {
                this.processDatabase(dbFile);
            } catch (Exception e) {
                DebugLogger.error("[Importer] Failed to process file " + dbFile.getAbsolutePath() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        DebugLogger.info("[Importer] Batch import finished.");
    }

    @SuppressWarnings("unchecked")
    private void processDatabase(File dbFile) throws SQLException {
        DebugLogger.info("[Importer] Processing DB: " + dbFile.getName());
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            if (conn == null) throw new SQLException("Could not connect to database: " + dbFile.getAbsolutePath());

            String sql = "SELECT uuid, data FROM users WHERE data IS NOT NULL AND TRIM(data) <> '' AND data <> '[]'";
            int processed = 0;
            int ignored = 0;

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                List<PlayerMinions> batch = new ArrayList<>();
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("uuid"));
                    String dataJson = rs.getString("data");

                    List<MinionOld> oldMinions;
                    try {
                        oldMinions = (List<MinionOld>) ItemBuilder.GSON.fromJson(
                                dataJson, new TypeToken<List<MinionOld>>() {}.getType());
                    } catch (Exception e) {
                        DebugLogger.warn("   -> Error parsing data from " + playerId + ": " + e.getMessage());
                        ignored++;
                        continue;
                    }

                    if (oldMinions == null || oldMinions.isEmpty()) {
                        ignored++;
                        continue;
                    }

                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
                    if (offlinePlayer.getName() == null) {
                        ignored++;
                        continue;
                    }

                    PlayerMinions playerMinions = new PlayerMinions(offlinePlayer.getUniqueId(), offlinePlayer.getName());
                    for (MinionOld old : oldMinions) {
                        MinionTemplate template = BeeMinions.getAPI().getMinionTemplate(old.id());
                        if (template == null) continue;

                        MinionType minionType = switch (template.getMinionType()) {
                            case COMBAT   -> MinionType.KILLER;
                            case MINING   -> MinionType.MINER;
                            case FARMING  -> MinionType.FARMER;
                            case FISHING  -> MinionType.FISHERMAN;
                            case FORAGING -> MinionType.LUMBERJACK;
                            default -> throw new MatchException(null, null);
                        };

                        MinionInventory inventory = new MinionInventory(new ArrayList<>(), 0L, old.maxSlots());
                        for (ItemStack stack : old.storage()) {
                            if (stack != null) {
                                inventory.addItems(MinionInventory.ItemData.of(UUID.randomUUID(), stack, stack.getAmount()));
                            }
                        }

                        Location loc = old.convertLocation();
                        MinionConfig minionConfig = this.minionManager.get(old.id());
                        if (minionConfig == null) continue;

                        Minion minion = new Minion(UUID.randomUUID(), old.id(), playerId,
                                minionType, inventory, minionConfig, new Location3f(loc));
                        minion.setCacheProductionSpeed(minionConfig.getProductionSpeed(old.level()));
                        minion.setStatus(!old.lastPositionCorrectly() ? MinionStatus.POSITION_INVALID : MinionStatus.IDLE);
                        minion.setLastConnection(System.currentTimeMillis());
                        minion.setLastGenerated(System.currentTimeMillis());
                        minion.setLevel(old.level());
                        minion.setSkinLevel(minionConfig.getSkinLevel(minion.getLevel()));
                        playerMinions.addMinion(minion);
                    }

                    batch.add(playerMinions);
                    if (++processed % 500 == 0) {
                        DebugLogger.info("   -> Processed " + processed + " users...");
                    }
                }
                this.database.savePlayersMinions(batch);
            }
            DebugLogger.info("[Importer] Batch import finished. Processed=" + processed + ", Ignored=" + ignored);
        }
    }
}
