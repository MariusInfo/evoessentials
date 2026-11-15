package org.evocraft.evoessentials.vanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.client.ClientVanishData;
import org.evocraft.evoessentials.EvoEssentials;
import org.evocraft.evoessentials.fly.FlyOverlay;

@Mod.EventBusSubscriber(modid = EvoEssentials.MODID, value = Dist.CLIENT)
public class VanishOverlay {

    public static boolean isVanished = false;

    public static boolean isLocalVanished(Minecraft mc) {
        if (isVanished) return true;
        return mc != null && mc.player != null && ClientVanishData.vanishedPlayers.contains(mc.player.getUUID());
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        isVanished = false;
        ClientVanishData.vanishedPlayers.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.font == null || mc.options.hideGui || mc.options.renderDebug) return;
        if (mc.options.keyPlayerList.isDown()) return;
        if (!isLocalVanished(mc)) return;

        GuiGraphics g = event.getGuiGraphics();
        int h = event.getWindow().getGuiScaledHeight();

        double guiScale = mc.getWindow().getGuiScale();
        float hudScale = 1.0f;
        if (guiScale >= 4) hudScale = 0.70f;
        else if (guiScale >= 3) hudScale = 0.85f;

        String title = "§a§l✦ VANISHED";
        String subtitle = "§7Hidden from players";

        int boxWidth = Math.max(126, Math.max(mc.font.width(title), mc.font.width(subtitle)) + 20);
        int boxHeight = 30;

        g.pose().pushPose();
        g.pose().scale(hudScale, hudScale, 1.0f);

        int x = 8;
        int y = Math.round(((h / hudScale) - boxHeight) / 2.0f);
        if (FlyOverlay.shouldShowFlyHud(mc)) {
            y -= (boxHeight + 6);
        }

        g.fill(x + 2, y + 2, x + boxWidth + 2, y + boxHeight + 2, 0x45000000);
        g.fillGradient(x, y, x + boxWidth, y + boxHeight, 0xCC0D140D, 0xCC1B2B1B);
        g.renderOutline(x, y, boxWidth, boxHeight, 0x883A592D);
        g.fill(x, y, x + 2, y + boxHeight, 0xFF83B755);

        g.drawString(mc.font, title, x + 9, y + 5, 0xFFFFFF, true);
        g.drawString(mc.font, subtitle, x + 9, y + 17, 0xAAAAAA, true);

        g.pose().popPose();
    }
}
