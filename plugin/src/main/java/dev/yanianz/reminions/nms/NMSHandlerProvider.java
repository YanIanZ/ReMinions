package dev.yanianz.reminions.nms;

import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.Bukkit;

public class NMSHandlerProvider {
    // Candidate adapter packages, in the order they're probed at runtime. The first one whose
    // classes link successfully wins. Order: newest first, so a 26.1.x server picks v26_1_2 even
    // though v1_21_11 classes are also bundled in the shaded jar.
    // Probe order is newest first so a Paper 26.2.x server prefers v26_1_2 over the 1.21.11
    // legacy adapter; 26.2 keeps the same craftbukkit/NMS surface so the v26_1_2 classes link
    // cleanly there too (the adapter group is intentionally named after the lowest 26.x build
    // it targets, not the highest one it's known to work on).
    //
    // The buckets below v1_21_11 are API-only — they share the plugin's ApiBackedNmsAdapter,
    // resolve particle names per-era via ParticleResolver, and degrade ghost-recipe / title-
    // update operations to no-ops on versions where the NMS protocol isn't bundled.
    private static final String[] VERSION_GROUPS = {
            "26_1_2",   // Paper CalVer 26.1.x / 26.2.x
            "1_21_11",  // Paper 1.21.11 (paperweight-mapped)
            "1_21_8",   // 1.21.8 – 1.21.10
            "1_21_6",   // 1.21.6 – 1.21.7
            "1_21_5",   // 1.21.5
            "1_21_4",   // 1.21.4
            "1_21_2",   // 1.21.2 – 1.21.3
            "1_21",     // 1.21.0 – 1.21.1
            "1_20_5",   // 1.20.5 – 1.20.6 (new particle names)
            "1_20_3",   // 1.20.3 – 1.20.4
            "1_20_2",   // 1.20.2
            "1_20"      // 1.20.0 – 1.20.1
    };

    private static NMSHandler handler;
    private static ParticleBridge particleBridge;
    private static ItemBridge itemBridge;
    private static InventoryBridge inventoryBridge;
    private static String resolvedGroup;

    public static NMSHandler getHandler() {
        if (handler == null) {
            NMSHandler loaded = loadHandler();
            handler = loaded != null ? loaded : FallbackBridges.NMS_HANDLER;
        }
        return handler;
    }

    public static ParticleBridge getParticleBridge() {
        if (particleBridge == null) {
            ParticleBridge loaded = loadParticleBridge();
            particleBridge = loaded != null ? loaded : FallbackBridges.PARTICLE;
        }
        return particleBridge;
    }

    public static ItemBridge getItemBridge() {
        if (itemBridge == null) {
            ItemBridge loaded = loadItemBridge();
            itemBridge = loaded != null ? loaded : FallbackBridges.ITEM;
        }
        return itemBridge;
    }

    public static InventoryBridge getInventoryBridge() {
        if (inventoryBridge == null) {
            InventoryBridge loaded = loadInventoryBridge();
            inventoryBridge = loaded != null ? loaded : FallbackBridges.INVENTORY;
        }
        return inventoryBridge;
    }

    /**
     * Probes each candidate adapter for {@code kind} in order until one loads cleanly. Caches the
     * winning group so subsequent bridges (Particle/Item/Inventory) skip straight to that adapter.
     */
    @SuppressWarnings("unchecked")
    private static <T> T tryLoad(String classPattern, Class<T> iface, String kind) {
        if (resolvedGroup != null) {
            String className = classPattern.replace("{V}", resolvedGroup);
            try {
                Class<T> cls = (Class<T>) Class.forName(className);
                return cls.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                DebugLogger.warn("⚠️ " + kind + " for resolved group " + resolvedGroup + " failed: " + t.getMessage());
                return null;
            }
        }

        for (String group : VERSION_GROUPS) {
            String className = classPattern.replace("{V}", group);
            try {
                Class<T> cls = (Class<T>) Class.forName(className);
                T instance = cls.getDeclaredConstructor().newInstance();
                resolvedGroup = group;
                DebugLogger.info("✅ NMS adapter resolved: " + group + " (server: " + Bukkit.getBukkitVersion() + ")");
                return instance;
            } catch (ClassNotFoundException e) {
                DebugLogger.warn("❌ No " + kind + " implementation for group " + group);
            } catch (LinkageError e) {
                // Class loaded but its NMS references don't resolve on this server (e.g. running
                // on Paper 26.1.x while v1_21_11 adapter was compiled against 1.21.11 internals).
                DebugLogger.warn("⚠️ " + kind + " for " + group + " incompatible with this server: " + e.getMessage());
            } catch (Exception e) {
                DebugLogger.warn("⚠️ Error initializing " + kind + " for " + group + ": " + e.getMessage());
            }
        }
        return null;
    }

    private static NMSHandler loadHandler() {
        return tryLoad("dev.yanianz.v{V}.NMSHandlerImpl_{V}", NMSHandler.class, "NMSHandler");
    }

    private static ParticleBridge loadParticleBridge() {
        return tryLoad("dev.yanianz.v{V}.ParticleBridge_v{V}", ParticleBridge.class, "ParticleBridge");
    }

    private static ItemBridge loadItemBridge() {
        return tryLoad("dev.yanianz.v{V}.ItemBridge_v{V}", ItemBridge.class, "ItemBridge");
    }

    private static InventoryBridge loadInventoryBridge() {
        return tryLoad("dev.yanianz.v{V}.InventoryBridge_v{V}", InventoryBridge.class, "InventoryBridge");
    }
}
