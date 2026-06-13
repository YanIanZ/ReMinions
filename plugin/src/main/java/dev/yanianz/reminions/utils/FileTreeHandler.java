package dev.yanianz.reminions.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import dev.yanianz.reminions.ReMinions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class FileTreeHandler<T> {
    private static final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(ReMinions.class);
    private final Map<String, T> loaded = new HashMap<>();
    private final File rootFolder;
    private final String resourcePath;
    private final List<String> defaultResources;
    private final BiConsumer<FileConfiguration, String> onRawLoad;
    private final BiConsumer<T, String> onLoaded;

    public FileTreeHandler(String folderName, String resourceBase, List<String> defaults,
                           BiConsumer<FileConfiguration, String> onRawLoad, BiConsumer<T, String> onLoaded) {
        this.rootFolder = new File(plugin.getDataFolder(), folderName);
        this.resourcePath = resourceBase.endsWith("/") ? resourceBase : resourceBase + "/";
        this.defaultResources = defaults != null ? defaults : List.of();
        this.onRawLoad = onRawLoad;
        this.onLoaded = onLoaded;
        if (!this.rootFolder.exists()) {
            this.rootFolder.mkdirs();
            this.copyDefaults();
        }
        this.loadAll();
    }

    public void loadAll() {
        this.loaded.clear();
        this.loadFolder(this.rootFolder);
    }

    public abstract T load(ConfigurationSection section, String id);

    private void copyDefaults() {
        // Auto-discover all .yml resources under resourcePath in the plugin jar and copy them to disk.
        // Falls back to the hardcoded defaultResources list if jar walking fails.
        boolean discovered = false;
        try {
            File jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jarFile.isFile()) {
                try (JarFile jar = new JarFile(jarFile)) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (entry.isDirectory() || !name.startsWith(this.resourcePath) || !name.endsWith(".yml")) continue;
                        String rel = name.substring(this.resourcePath.length());
                        File target = new File(this.rootFolder, rel);
                        if (target.exists()) continue;
                        target.getParentFile().mkdirs();
                        try (InputStream is = jar.getInputStream(entry); OutputStream os = new FileOutputStream(target)) {
                            is.transferTo(os);
                        }
                    }
                    discovered = true;
                }
            }
        } catch (Exception e) {
            DebugLogger.warn("Auto-discovery of resources failed, falling back to defaults list: " + e.getMessage());
        }
        if (discovered) return;
        for (String resource : this.defaultResources) {
            File target = new File(this.rootFolder, resource);
            if (!target.exists()) {
                try {
                    target.getParentFile().mkdirs();
                    plugin.saveResource(this.resourcePath + resource, false);
                } catch (Exception e) {
                    DebugLogger.warn("Failed to copy resource: " + resource);
                    e.printStackTrace();
                }
            }
        }
    }

    private void loadFolder(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                this.loadFolder(file);
            } else if (file.getName().endsWith(".yml")) {
                String id = file.getName().replace(".yml", "");
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                if (this.onRawLoad != null) this.onRawLoad.accept(cfg, id);
                try {
                    T obj = this.load(cfg, id);
                    if (obj != null) {
                        this.loaded.put(id.toLowerCase(), obj);
                        if (this.onLoaded != null) this.onLoaded.accept(obj, id);
                    }
                } catch (Exception e) {
                    DebugLogger.warn("Failed to load: " + file.getPath());
                    e.printStackTrace();
                }
            }
        }
    }

    public T get(String key) {
        return key == null ? null : this.loaded.get(key.toLowerCase());
    }

    public T get(String primary, String fallback) {
        T result = this.loaded.get(primary.toLowerCase());
        return result != null ? result : this.loaded.get(fallback);
    }

    public Collection<T> getAll()          { return this.loaded.values(); }
    public boolean contains(String key)    { return this.loaded.containsKey(key.toLowerCase()); }
    public int size()                      { return this.loaded.size(); }
    public Set<String> keys()             { return this.loaded.keySet(); }
}
