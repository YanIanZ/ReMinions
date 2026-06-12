package dev.yanianz.reminions.utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import dev.yanianz.reminions.ReMinions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class FileHandler {
    private static final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(ReMinions.class);
    private final File path;
    private FileConfiguration config;

    public FileHandler(String fileName) {
        this.path = new File(plugin.getDataFolder(), fileName);
        if (!this.path.exists()) plugin.saveResource(fileName, false);
        this.config = YamlConfiguration.loadConfiguration(this.path);
    }

    public abstract void load();

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(this.path);
        this.load();
    }

    public void save() {
        try {
            this.config.save(this.path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getString(String key)                   { return this.config.getString(key); }
    public String getString(String key, String def)       { return this.config.getString(key, def); }
    public int getInt(String key)                         { return this.config.getInt(key); }
    public int getInt(String key, int def)                { return this.config.getInt(key, def); }
    public boolean getBoolean(String key)                 { return this.config.getBoolean(key); }
    public boolean getBoolean(String key, boolean def)    { return this.config.getBoolean(key, def); }
    public double getDouble(String key)                   { return this.config.getDouble(key); }
    public double getDouble(String key, double def)       { return this.config.getDouble(key, def); }
    public List<String> getStringList(String key)         { return this.config.getStringList(key); }
    public ConfigurationSection getSection(String key)    { return this.config.getConfigurationSection(key); }
    public boolean contains(String key)                   { return this.config.contains(key); }
    public void set(String key, Object value)             { this.config.set(key, value); }

    public void setAndSave(String key, Object value) {
        this.config.set(key, value);
        this.save();
    }

    public ConfigurationSection getSectionOrThrow(String key, String errorMsg) {
        ConfigurationSection section = this.getSection(key);
        if (section == null) throw new IllegalStateException(errorMsg);
        return section;
    }

    public File getPath()             { return this.path; }
    public FileConfiguration getConfig(){ return this.config; }
}
