package net.stones_ef_bridge.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.stones_ef_bridge.StonesEfBridge;

/**
 * Verwaltet den Netzwerk-Kanal für die Synchronisierung zwischen Server und Client.
 */
public class StonesNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StonesEfBridge.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(SPacketSyncRarities.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SPacketSyncRarities::encode)
                .decoder(SPacketSyncRarities::decode)
                .consumerNetworkThread(SPacketSyncRarities::handle)
                .add();
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        if (player.connection != null) {
            CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }
}