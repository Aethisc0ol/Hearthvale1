package com.hearthvale.client.book;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.hearthvale.Hearthvale;

/** Pixel-art drawing helpers in the palette of the book art. Coordinates are in art pixels. */
public final class Pix {
    private Pix() {}

    public static final int CREAM = 0xFFE7D5B3, TAN = 0xFFD7B594, TAN_D = 0xFFC09473, TAN_L = 0xFFEBDCC0;
    public static final int BROWN = 0xFFAD7757, BROWN_D = 0xFF884B2B, MAROON = 0xFF602C2C, DARK = 0xFF341C27, WHITE = 0xFFF4EBD9;
    public static final int GREEN = 0xFF75A743, GREEN_D = 0xFF468232, BLUE = 0xFF4F8FBA, BLUE_D = 0xFF3C5E8B;
    public static final int RED = 0xFFCF573C, RED_D = 0xFFA53030, PURPLE = 0xFF9C4D9A, PURPLE_D = 0xFF6F3578;

    public static final ResourceLocation HEART_FULL = tex("heart_full");
    public static final ResourceLocation HEART_EMPTY = tex("heart_empty");

    static ResourceLocation tex(String n) {
        return ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "textures/gui/book/" + n + ".png");
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, c); }

    /** Rectangle with the four corner pixels removed - the look of every card in the reference. */
    public static void notched(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + w, y + h - 1, c);
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int shade) {
        notched(g, x, y, w, h, fill);
        if (shade != 0) g.fill(x + 1, y + h - 1, x + w - 1, y + h, shade);
    }

    /** Dialogue / popup panel: dark outline, two tan rings, cream fill, stepped corners. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        notched(g, x, y, w, h, DARK);
        notched(g, x + 1, y + 1, w - 2, h - 2, TAN_D);
        notched(g, x + 2, y + 2, w - 4, h - 4, TAN);
        rect(g, x + 3, y + 3, w - 6, h - 6, CREAM);
    }

    public static void chip(GuiGraphics g, Font font, int x, int y, int w, int h, int fill, int shade, Component text) {
        card(g, x, y, w, h, fill, shade);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2 + 1 - 1, WHITE, false);
    }

    public static void diamond(GuiGraphics g, int cx, int cy, int r, int c) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, c);
        }
    }

    /** Row of downward triangles (the scalloped dividers under the headings in the reference). */
    public static void scallops(GuiGraphics g, int x, int y, int w, int c) {
        for (int cx = x + 4; cx + 3 <= x + w; cx += 8) {
            g.fill(cx - 3, y, cx + 4, y + 1, c);
            g.fill(cx - 2, y + 1, cx + 3, y + 2, c);
            g.fill(cx - 1, y + 2, cx + 2, y + 3, c);
            g.fill(cx, y + 3, cx + 1, y + 4, c);
        }
    }

    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x + 4, y, x + w - 4, y + 1, TAN_D);
        diamond(g, x + 2, y, 2, TAN_D);
        diamond(g, x + w - 3, y, 2, TAN_D);
    }

    public static void arrow(GuiGraphics g, int x, int y, int dir, int c) {
        for (int k = 0; k < 4; k++) {
            switch (dir) {
                case 0 -> { g.fill(x + 3 - k, y + k, x + 4, y + k + 1, c); g.fill(x + 3 - k, y + 6 - k, x + 4, y + 7 - k, c); }
                case 1 -> { g.fill(x, y + k, x + k + 1, y + k + 1, c); g.fill(x, y + 6 - k, x + k + 1, y + 7 - k, c); }
                default -> g.fill(x + k, y + k, x + 7 - k, y + k + 1, c);
            }
        }
    }

    public static void check(GuiGraphics g, int x, int y, int c) {
        int[][] px = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {7, 0}};
        for (int[] p : px) g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, c);
    }

    public static void bullet(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 1, y, x + 2, y + 3, c);
        g.fill(x, y + 1, x + 3, y + 2, c);
    }

    /** One 9x9 heart, filled from the bottom in quarters (0..4). */
    public static void heart(GuiGraphics g, int x, int y, int quarters) {
        RenderSystem.enableBlend();
        g.blit(HEART_EMPTY, x, y, 0f, 0f, 9, 9, 9, 9);
        if (quarters > 0) {
            int hh = Math.min(9, (int) Math.ceil(9 * quarters / 4.0));
            g.blit(HEART_FULL, x, y + 9 - hh, 0f, (float) (9 - hh), 9, hh, 9, 9);
        }
    }

    // ---- friendship display: 0/8 while getting to know someone, 8/10 when dating, 10/14 when engaged or married
    public static int maxHearts(String status, boolean romance) {
        return switch (status) {
            case "DATING" -> 10;
            case "ENGAGED", "MARRIED" -> 14;
            default -> romance ? 8 : 10;
        };
    }

    public static int quartersOfCurrentHeart(int points, int maxHearts) {
        int whole = points / 250;
        if (whole >= maxHearts) return 4;
        return Math.min(4, (points % 250) * 4 / 250);
    }
}
