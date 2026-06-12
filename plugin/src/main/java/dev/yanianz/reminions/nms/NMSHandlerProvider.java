package dev.yanianz.reminions.nms;

import dev.yanianz.reminions.utils.DebugLogger;

public class NMSHandlerProvider {
    private static NMSHandler handler;
    private static ParticleBridge particleBridge;
    private static ItemBridge itemBridge;
    private static InventoryBridge inventoryBridge;

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

    private static String resolveVersionGroup() {
        // Project targets Minecraft 1.21.11+. Older versions degrade to the Bukkit-API
        // FallbackBridges defined in this package — they keep menus/inventory working,
        // only the recipe-book ghost-packet feature is suppressed.
        return "1_21_11";
    }

    @SuppressWarnings("unchecked")
    private static NMSHandler loadHandler() {
        String versionGroup = resolveVersionGroup();
        String className = "dev.yanianz.v" + versionGroup + ".NMSHandlerImpl_" + versionGroup;
        try {
            Class<NMSHandler> cls = (Class<NMSHandler>) Class.forName(className);
            return cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            DebugLogger.warn("❌ No NMS implementation found for version: " + versionGroup);
        } catch (Exception e) {
            DebugLogger.warn("⚠️ Error initializing NMS implementation for version: " + versionGroup + " (" + e.getMessage() + ")");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static ParticleBridge loadParticleBridge() {
        String versionGroup = resolveVersionGroup();
        String className = "dev.yanianz.v" + versionGroup + ".ParticleBridge_v" + versionGroup;
        try {
            Class<ParticleBridge> cls = (Class<ParticleBridge>) Class.forName(className);
            return cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            DebugLogger.warn("❌ No ParticleBridge implementation found for version: " + versionGroup);
        } catch (Exception e) {
            DebugLogger.warn("⚠️ Error initializing ParticleBridge for version: " + versionGroup + " (" + e.getMessage() + ")");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static ItemBridge loadItemBridge() {
        String versionGroup = resolveVersionGroup();
        String className = "dev.yanianz.v" + versionGroup + ".ItemBridge_v" + versionGroup;
        try {
            Class<ItemBridge> cls = (Class<ItemBridge>) Class.forName(className);
            return cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            DebugLogger.warn("❌ No ItemBridge implementation found for version: " + versionGroup);
        } catch (Exception e) {
            DebugLogger.warn("⚠️ Error initializing ItemBridge for version: " + versionGroup + " (" + e.getMessage() + ")");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static InventoryBridge loadInventoryBridge() {
        String versionGroup = resolveVersionGroup();
        String className = "dev.yanianz.v" + versionGroup + ".InventoryBridge_v" + versionGroup;
        try {
            Class<InventoryBridge> cls = (Class<InventoryBridge>) Class.forName(className);
            return cls.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            DebugLogger.warn("❌ No InventoryBridge implementation found for version: " + versionGroup);
        } catch (Exception e) {
            DebugLogger.warn("⚠️ Error initializing InventoryBridge for version: " + versionGroup + " (" + e.getMessage() + ")");
        }
        return null;
    }
}
