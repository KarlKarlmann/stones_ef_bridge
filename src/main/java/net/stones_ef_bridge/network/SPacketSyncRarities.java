package net.stones_ef_bridge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.stones_ef_bridge.util.SkillRarityManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C-Paket, das die konfigurierten Raritäten des Servers an den Client überträgt.
 */
public class SPacketSyncRarities {
    private final Map<String, Integer> rarities;

    public SPacketSyncRarities(Map<String, Integer> rarities) {
        this.rarities = rarities;
    }

    public static void encode(SPacketSyncRarities msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.rarities.size());
        msg.rarities.forEach((key, val) -> {
            buf.writeUtf(key);
            buf.writeInt(val);
        });
    }

    public static SPacketSyncRarities decode(FriendlyByteBuf buf) {
		int size = buf.readInt();
		if (size < 0 || size > 10000) {
			return new SPacketSyncRarities(new HashMap<>()); // Leeres Fallback
		}
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(buf.readUtf(), buf.readInt());
        }
        return new SPacketSyncRarities(map);
    }

    public static void handle(SPacketSyncRarities msg, Supplier<NetworkEvent.Context> ctxGetter) {
        NetworkEvent.Context ctx = ctxGetter.get();
        ctx.enqueueWork(() -> {
            SkillRarityManager.setSynchronizedConfigs(msg.rarities);
        });
        ctx.setPacketHandled(true);
    }
}