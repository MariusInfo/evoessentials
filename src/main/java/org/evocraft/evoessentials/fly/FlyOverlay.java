package org.evocraft.evoessentials.fly;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.client.ClientFlyData;
import org.evocraft.evoessentials.EvoEssentials;

@Mod.EventBusSubscriber(modid = EvoEssentials.MODID, value = Dist.CLIENT)
public class FlyOverlay {

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFlyData.isFlying = false;
        ClientFlyData.timeRemainingSeconds = 0;
    }

    public static boolean shouldShowFlyHud(Minecraft mc) {
        if (mc == null || mc.player == null || mc.options.hideGui || mc.options.renderDebug) return false;
        if (mc.options.keyPlayerList.isDown()) return false;
        if (mc.player.isSpectator()) return false;
        return mc.player.getAbilities().flying || (!mc.player.isCreative() && mc.player.getAbilities().mayfly);
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || !shouldShowFlyHud(mc)) return;

        GuiGraphics g = event.getGuiGraphics();
        int h = event.getWindow().getGuiScaledHeight();

        double guiScale = mc.getWindow().getGuiScale();
        float hudScale = 1.0f;
        if (guiScale >= 4) hudScale = 0.70f;
        else if (guiScale >= 3) hudScale = 0.85f;

        boolean isAirborne = mc.player.getAbilities().flying;
        String title = isAirborne ? "§b§l☄ FLY ACTIVE" : "§a§l☄ FLY READY";

        int left = ClientFlyData.timeRemainingSeconds;
        String timeText;
        if (mc.player.isCreative() || left < 0) {
            timeText = "§aUnlimited";
        } else {
            int hTime = left / 3600;
            int mTime = (left % 3600) / 60;
            if (left >= 3600) {
                String hoursText = (hTime == 1) ? "1 hour" : hTime + " hours";
                timeText = hoursText + (mTime > 0 ? ", " + mTime + " min" : "");
            } else if (left >= 60) {
                timeText = mTime + " min";
            } else {
                timeText = left + " seconds";
            }

            if (left <= 60) timeText = "§c" + timeText;
            else if (left <= 300) timeText = "§e" + timeText;
            else timeText = "§a" + timeText;
        }

        String subtitle = "§7Time: " + timeText;

        int boxWidth = Math.max(126, Math.max(mc.font.width(title), mc.font.width(subtitle)) + 20);
        int boxHeight = 30;

        g.pose().pushPose();
        g.pose().scale(hudScale, hudScale, 1.0f);

        int x = 8;
        int y = Math.round(((h / hudScale) - boxHeight) / 2.0f);

        int accentColor = isAirborne ? 0xFF55FFFF : 0xFF83B755;
        int borderColor = isAirborne ? 0x88386C78 : 0x883A592D;

        g.fill(x + 2, y + 2, x + boxWidth + 2, y + boxHeight + 2, 0x45000000);
        g.fillGradient(x, y, x + boxWidth, y + boxHeight, 0xCC0D140D, 0xCC182618);
        g.renderOutline(x, y, boxWidth, boxHeight, borderColor);
        g.fill(x, y, x + 2, y + boxHeight, accentColor);

        g.drawString(mc.font, title, x + 9, y + 5, 0xFFFFFF, true);
        g.drawString(mc.font, subtitle, x + 9, y + 17, 0xFFFFFF, true);

        g.pose().popPose();
    }
}
