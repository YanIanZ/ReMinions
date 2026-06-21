package dev.yanianz.reminions.nms;

import dev.yanianz.reminions.utils.DebugLogger;
import org.bukkit.Bukkit;

public class NMSHandlerProvider {
    // Candidate adapter packages, in the order they're probed at runtime. The first one whose
    // classes link successfully wins. Order: newest first, so a 26.1.x server picks v26_1_2 even
    // though v1_21_11 classes are also bundled in the shaded jar.
    private static final String[] VERSION_GROUPS = { "26_1_2", "1_21_11" };

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
