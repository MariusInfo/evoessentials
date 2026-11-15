package org.evocraft.evoessentials.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.evocraft.evoessentials.EvoEssentials;

import java.util.Optional;

public class EssentialsPacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EvoEssentials.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static boolean isRegistered = false;

    public static void register() {
        if (isRegistered) return;
        isRegistered = true;

        INSTANCE.registerMessage(
                packetId++,
                C2S_HomeActionPacket.class,
                C2S_HomeActionPacket::toBytes,
                C2S_HomeActionPacket::new,
                C2S_HomeActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        INSTANCE.registerMessage(
                packetId++,
                C2S_TpaActionPacket.class,
                C2S_TpaActionPacket::toBytes,
                C2S_TpaActionPacket::new,
                C2S_TpaActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        INSTANCE.registerMessage(
                packetId++,
                S2C_SyncHomesPacket.class,
                S2C_SyncHomesPacket::toBytes,
                S2C_SyncHomesPacket::new,
                S2C_SyncHomesPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        if (player != null && !player.hasDisconnected()) {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }
}
