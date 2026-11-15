package org.evocraft.evoessentials.network;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evoessentials.EvoEssentials;
import org.evocraft.evoessentials.client.ClientHomeData;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = EvoEssentials.MODID, value = Dist.CLIENT)
public class EssentialsClientPacketHandler {
    public static void handleSyncHomes(int currentCount, int maxCount, List<String> homes) {
        ClientHomeData.currentHomesCount = currentCount;
        ClientHomeData.maxHomesCount = maxCount;
        ClientHomeData.playerHomes = homes == null ? new ArrayList<>() : new ArrayList<>(homes);

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.screen != null) {
            mc.screen.init(
                    mc,
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight()
            );
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHomeData.currentHomesCount = 0;
        ClientHomeData.maxHomesCount = 1;
        ClientHomeData.playerHomes = new ArrayList<>();
    }
}
