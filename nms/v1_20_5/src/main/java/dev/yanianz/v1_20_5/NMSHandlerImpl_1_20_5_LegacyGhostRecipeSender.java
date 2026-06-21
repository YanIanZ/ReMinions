package dev.yanianz.v1_20_5;

import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * Companion to {@link NMSHandlerImpl_1_20_5}. Owns the legacy
 * {@link ClientboundPlaceGhostRecipePacket} construction + dispatch so the main handler stays
 * tidy. Reflective ctor lookup tolerates the {@code (int, ResourceLocation)} vs
 * {@code (int, RecipeHolder)} signature drift across 1.20.5 → 1.21.1.
 */
final class NMSHandlerImpl_1_20_5_LegacyGhostRecipeSender {

    private NMSHandlerImpl_1_20_5_LegacyGhostRecipeSender() {}

    static void send(CraftPlayer player, NamespacedKey key) throws Throwable {
        ServerPlayer serverPlayer = player.getHandle();
        int containerId = serverPlayer.containerMenu.containerId;
        // Paper 1.20.5 exposes ResourceLocation.parse as the stable factory — the public
        // ctor was hidden post-1.20.4 and `fromNamespaceAndPath` arrives in 1.21.
        ResourceLocation location = ResourceLocation.tryParse(key.getNamespace() + ":" + key.getKey());
        if (location == null) return;
        ClientboundPlaceGhostRecipePacket packet = (ClientboundPlaceGhostRecipePacket)
                ClientboundPlaceGhostRecipePacket.class
                        .getConstructor(int.class, ResourceLocation.class)
                        .newInstance(containerId, location);
        serverPlayer.connection.send(packet);
    }
}
