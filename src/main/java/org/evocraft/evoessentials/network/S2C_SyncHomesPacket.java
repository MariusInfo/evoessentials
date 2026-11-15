package org.evocraft.evoessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2C_SyncHomesPacket {
    private final int currentCount;
    private final int maxCount;
    private final List<String> homes;

    public S2C_SyncHomesPacket(int currentCount, int maxCount, List<String> homes) {
        this.currentCount = currentCount; this.maxCount = maxCount; this.homes = homes;
    }

    public S2C_SyncHomesPacket(FriendlyByteBuf buf) {
        this.currentCount = buf.readInt();
        this.maxCount = buf.readInt();
        this.homes = new ArrayList<>();
        int size = Math.max(0, Math.min(256, buf.readInt()));
        for (int i = 0; i < size; i++) this.homes.add(buf.readUtf(64));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(currentCount);
        buf.writeInt(maxCount);
        List<String> safeHomes = homes != null ? homes : List.of();
        int count = Math.min(256, safeHomes.size());
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            String home = safeHomes.get(i);
            // Blindaj suprem anti-crash!
            buf.writeUtf(home != null ? home : "Unknown", 64);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.evocraft.evoessentials.network.EssentialsClientPacketHandler.handleSyncHomes(currentCount, maxCount, homes)));
        supplier.get().setPacketHandled(true);
        return true;
    }
}
