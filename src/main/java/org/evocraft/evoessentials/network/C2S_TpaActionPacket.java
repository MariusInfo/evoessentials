package org.evocraft.evoessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.evocraft.evoessentials.teleport.TpaManager;
import org.evocraft.evocombat.combat.CombatLogManager;

import java.util.function.Supplier;

public class C2S_TpaActionPacket {
    private final String action; // "tpa" sau "tpahere"
    private final String targetName;

    public C2S_TpaActionPacket(String action, String targetName) {
        this.action = action;
        this.targetName = targetName;
    }

    public C2S_TpaActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readUtf(16);
        this.targetName = buf.readUtf(32);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(action != null ? action : "", 16);
        buf.writeUtf(targetName != null ? targetName : "", 32);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // Protecție Combat
                if (CombatLogManager.isInCombat(player)) {
                    player.sendSystemMessage(Component.literal("§c✖ You cannot use teleport requests (TPA) while in combat!"));
                    return;
                }
                TpaManager.sendTpaRequest(player, targetName, action.equals("tpahere"));
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
