package dev.yanianz.reminions;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import java.io.File;
import java.util.Objects;
import dev.yanianz.reminions.api.BeeAPI;
import dev.yanianz.reminions.command.CommandManager;
import dev.yanianz.reminions.command.impl.DeleteMinionCommand;
import dev.yanianz.reminions.command.impl.GetMinionCommand;
import dev.yanianz.reminions.command.impl.GetModifierCommand;
import dev.yanianz.reminions.command.impl.GetSkinCommand;
import dev.yanianz.reminions.command.impl.GetStorageCommand;
import dev.yanianz.reminions.command.impl.ImportCommand;
import dev.yanianz.reminions.command.impl.ReloadCommand;
import dev.yanianz.reminions.command.impl.ResetAllUniqueMinionsCommand;
import dev.yanianz.reminions.command.impl.ResetUniqueMinionsCommand;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.database.Database;
import dev.yanianz.reminions.database.DatabaseMeta;
import dev.yanianz.reminions.database.impl.CachedDatabase;
import dev.yanianz.reminions.database.impl.MySQLDatabase;
import dev.yanianz.reminions.database.impl.SQLDatabase;
import dev.yanianz.reminions.listener.AuraSkillListener;
import dev.yanianz.reminions.listener.BlockChangeListener;
import dev.yanianz.reminions.listener.EcoSkillListener;
import dev.yanianz.reminions.listener.EntityListener;
import dev.yanianz.reminions.listener.MenuListener;
import dev.yanianz.reminions.listener.PlayerListener;
import dev.yanianz.reminions.listener.RecipeBookListener;
import dev.yanianz.reminions.listener.RecipeListener;
import dev.yanianz.reminions.listener.StorageListener;
import dev.yanianz.reminions.managers.MenuManager;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.managers.ModifierManager;
import dev.yanianz.reminions.managers.PlayerManager;
import dev.yanianz.reminions.managers.SkinManager;
import dev.yanianz.reminions.managers.StorageManager;
import dev.yanianz.reminions.nms.NMSHandlerProvider;
import dev.yanianz.reminions.placeholder.ReMinionsExpansion;
import dev.yanianz.reminions.task.MinionThreadTask;
import dev.yanianz.reminions.utils.DebugLogger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReMinions extends JavaPlugin {

    private static ReMinions plugin;
    private static boolean recipesEnabled = true;

    private Config config0;
    private MinionManager minionManager;
    private SkinManager skinManager;
    private PlayerManager playerManager;
    private ModifierManager modifierManager;
    private StorageManager storageManager;
    private MenuManager menuManager;
    private Database database;
    private MinionThreadTask minionThreadTask;
    private Economy economy;
    private LuckPerms luckPerms;
    private BeeAPI api;

    @Override
    public void onEnable() {
        plugin = this;

        this.config0 = new Config();
        recipesEnabled = this.config0.getBoolean("settings.minions_recipes");

        File importDir = new File(this.getDataFolder(), "import");
        if (!importDir.exists()) importDir.mkdirs();

        DebugLogger.setEnabled(this.config0.getBoolean("settings.debug_enabled"));

        this.skinManager     = new SkinManager();
        this.minionManager   = new MinionManager();
        this.playerManager   = new PlayerManager();

        ConfigurationSection dbSection = this.config0.getSectionOrThrow("database", "Missing 'database' section");
        this.database = buildDatabase(dbSection);
        this.database.connect(buildDatabaseMeta(dbSection));

        this.modifierManager = new ModifierManager();
        this.storageManager  = new StorageManager();
        this.menuManager     = new MenuManager();

        this.minionThreadTask = new MinionThreadTask(
                this.playerManager, this.minionManager, this.modifierManager, this.skinManager);
        this.minionThreadTask.runTaskTimer(this, 0L, 1L);

        this.api = new BeeAPI(this.config0, this.playerManager, this.minionManager, this.skinManager);

        loadVault();
        loadLuckperms();
        loadAuraSkills();
        loadEcoSkills();
        loadPlaceholderAPI();

        CommandManager commandManager = new CommandManager(this);
        commandManager.register(new GetMinionCommand());
        commandManager.register(new GetModifierCommand());
        commandManager.register(new GetSkinCommand());
        commandManager.register(new GetStorageCommand());
        commandManager.register(new ImportCommand());
        commandManager.register(new ReloadCommand());
        commandManager.register(new DeleteMinionCommand());
        commandManager.register(new ResetAllUniqueMinionsCommand());
        commandManager.register(new ResetUniqueMinionsCommand());
        Objects.requireNonNull(this.getCommand("reminions")).setExecutor(commandManager);

        this.getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        this.getServer().getPluginManager().registerEvents(new MenuListener(), this);
        this.getServer().getPluginManager().registerEvents(new EntityListener(), this);
        this.getServer().getPluginManager().registerEvents(new StorageListener(this), this);
        this.getServer().getPluginManager().registerEvents(new BlockChangeListener(this), this);

        // Eagerly resolve NMS bridges so the first runtime call does not pay Class.forName + ctor latency mid-tick.
        NMSHandlerProvider.getHandler();
        NMSHandlerProvider.getInventoryBridge();
        NMSHandlerProvider.getItemBridge();
        NMSHandlerProvider.getParticleBridge();

        if (recipesEnabled) {
            this.getServer().getPluginManager().registerEvents(new RecipeListener(), this);
            this.getServer().getPluginManager().registerEvents(new RecipeBookListener(this.minionManager, this.config0), this);
        }
    }

    private Database buildDatabase(ConfigurationSection section) {
        String type = section.getString("type", "sqlite");
        if (type.equalsIgnoreCase("sqlite")) return new SQLDatabase();
        if (type.equalsIgnoreCase("mysql"))  return new MySQLDatabase();
        if (type.equalsIgnoreCase("redis"))  return new CachedDatabase();
        this.getServer().getPluginManager().disablePlugin(this);
        throw new IllegalArgumentException("Unknown database type: " + type);
    }

    private DatabaseMeta buildDatabaseMeta(ConfigurationSection section) {
        DatabaseMeta meta = new DatabaseMeta();
        meta.setFileName(section.getString("path", "players") + ".db");
        meta.setHost(section.getString("host", "localhost"));
        meta.setPort(section.getInt("port", 3306));
        meta.setUser(section.getString("user", "root"));
        meta.setPassword(section.getString("password", ""));
        meta.setDatabaseName(section.getString("database", "reminions"));
        meta.setRedisHost(section.getString("redis.host", "localhost"));
        meta.setRedisPort(section.getInt("redis.port", 3306));
        meta.setRedisPassword(section.getString("redis.password", ""));
        return meta;
    }

    @Override
    public void onDisable() {
        if (!this.minionThreadTask.isCancelled()) this.minionThreadTask.cancel();
        this.playerManager.getAllMinions().forEach(Minion::despawn);
        this.database.savePlayersMinions(this.playerManager.getPlayersSnapshot());
        this.database.disconnect();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Soft-dependency integration loaders
    // ─────────────────────────────────────────────────────────────────────────────

    private void loadLuckperms() {
        if (!isLuckPermsInstalled()) {
            DebugLogger.info("LuckPerms not found, skipping integration.");
            return;
        }
        try {
            this.luckPerms = LuckPermsProvider.get();
            DebugLogger.info("LuckPerms successfully hooked.");
        } catch (Exception e) {
            DebugLogger.info("LuckPerms not found, skipping integration.");
        }
    }

    private void loadEcoSkills() {
        if (this.getServer().getPluginManager().getPlugin("EcoSkills") == null) {
            DebugLogger.info("EcoSkills not found, skipping integration.");
            return;
        }
        this.getServer().getPluginManager()
                .registerEvents(new EcoSkillListener(this.minionManager, this.modifierManager, this.config0), this);
        DebugLogger.info("EcoSkills successfully hooked.");
    }

    private void loadPlaceholderAPI() {
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            DebugLogger.info("PlaceholderAPI not found, skipping integration.");
            return;
        }
        try {
            new ReMinionsExpansion(this).register();
            DebugLogger.info("PlaceholderAPI successfully hooked.");
        } catch (Throwable e) {
            DebugLogger.warn("Failed to hook into PlaceholderAPI: " + e.getMessage());
        }
    }

    private void loadAuraSkills() {
        if (this.getServer().getPluginManager().getPlugin("AuraSkills") == null) {
            DebugLogger.info("AuraSkills not found, skipping integration.");
            return;
        }
        try {
            AuraSkillsApi auraApi = AuraSkillsApi.get();
            if (auraApi == null) {
                DebugLogger.warn("AuraSkills API not available, integration skipped.");
                return;
            }
            this.getServer().getPluginManager()
                    .registerEvents(new AuraSkillListener(this.minionManager, this.modifierManager, this.config0, auraApi), this);
            DebugLogger.info("AuraSkills successfully hooked.");
        } catch (Throwable e) {
            DebugLogger.warn("Failed to hook into AuraSkills: " + e.getMessage());
        }
    }

    private void loadVault() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            DebugLogger.warn("Vault API not available, integration skipped.");
            return;
        }
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<Economy> rsp =
                (RegisteredServiceProvider<Economy>) this.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            DebugLogger.warn("Vault Economy Service not available, integration skipped.");
            return;
        }
        this.economy = rsp.getProvider();
        DebugLogger.info("Economy successfully hooked: " + this.economy.getName());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Getters
    // ─────────────────────────────────────────────────────────────────────────────

    public Config getConfig0()               { return this.config0; }
    public MinionManager getMinionManager()  { return this.minionManager; }
    public SkinManager getSkinManager()      { return this.skinManager; }
    public PlayerManager getPlayerManager()  { return this.playerManager; }
    public ModifierManager getModifierManager() { return this.modifierManager; }
    public StorageManager getStorageManager()   { return this.storageManager; }
    public MenuManager getMenuManager()      { return this.menuManager; }
    public Database getDatabase()            { return this.database; }
    public MinionThreadTask getMinionThreadTask() { return this.minionThreadTask; }
    public Economy getEconomy()              { return this.economy; }
    public LuckPerms getLuckPerms()          { return this.luckPerms; }
    public BeeAPI getApi()                   { return this.api; }

    public boolean isMMOItemsInstalled() {
        return plugin.getServer().getPluginManager().getPlugin("MMOItems") != null;
    }

    public boolean isLuckPermsInstalled() {
        return plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null;
    }

    public static ReMinions getPlugin() { return plugin; }
    public static boolean isRecipesEnabled()   { return recipesEnabled; }
    public static void setRecipesEnabled(boolean enabled) { recipesEnabled = enabled; }
}
