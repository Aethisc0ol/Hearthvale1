package com.hearthvale.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** GUI layer drawn above everything: fade, letterbox bars, title card and the "skip" hint. */
public final class CutsceneOverlay {
    private CutsceneOverlay() {}

    public static void render(GuiGraphics g, DeltaTracker dt) {
        if (!ClientCutscene.active) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = dt.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();

        float a = Mth.lerp(pt, ClientCutscene.fadeAlphaO, ClientCutscene.fadeAlpha);
        if (a > 0.004f) g.fill(0, 0, w, h, ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | (ClientCutscene.fadeColor & 0xFFFFFF));

        int bar = (int) (h * 0.11f * Mth.lerp(pt, ClientCutscene.barProgressO, ClientCutscene.barProgress));
        if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
            if (ClientCutscene.skippable && ClientCutscene.ticksActive < 240 && bar > 12) {
                String hint = "Press ESC to skip";
                g.drawString(mc.font, hint, w - mc.font.width(hint) - 8, h - bar / 2 - 4, 0xFF9A9A9A, false);
            }
        }

        if (!ClientCutscene.title.isEmpty()) {
            float age = ClientCutscene.titleAge + pt;
            float fade = Math.min(1f, Math.min(age / 10f, (ClientCutscene.titleTicks - age) / 10f));
            int alpha = (int) (Mth.clamp(fade, 0f, 1f) * 255);
            if (alpha > 8) {
                g.pose().pushPose();
                g.pose().translate(w / 2f, h / 2f - 12, 0);
                g.pose().scale(2f, 2f, 1f);
                g.drawCenteredString(mc.font, ClientCutscene.title, 0, 0, (alpha << 24) | 0xFFFFFF);
                g.pose().popPose();
            }
        }
    }
}
