package dev.yanianz.v1_21;

import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * Companion to {@link NMSHandlerImpl_1_21}. Owns the legacy
 * {@link ClientboundPlaceGhostRecipePacket} construction + dispatch.
 */
final class NMSHandlerImpl_1_21_LegacyGhostRecipeSender {

    private NMSHandlerImpl_1_21_LegacyGhostRecipeSender() {}

    static void send(CraftPlayer player, NamespacedKey key) throws Throwable {
        ServerPlayer serverPlayer = player.getHandle();
        int containerId = serverPlayer.containerMenu.containerId;
        ResourceLocation location = ResourceLocation.tryParse(key.getNamespace() + ":" + key.getKey());
        if (location == null) return;
        ClientboundPlaceGhostRecipePacket packet = (ClientboundPlaceGhostRecipePacket)
                ClientboundPlaceGhostRecipePacket.class
                        .getConstructor(int.class, ResourceLocation.class)
                        .newInstance(containerId, location);
        serverPlayer.connection.send(packet);
    }
}
