package com.hearthvale.client.book;

import com.hearthvale.client.NpcRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.hearthvale.client.book.Pix.*;

/**
 * Immediate-mode toolkit used by every page: draw something and, if it is clickable, register a hot-spot.
 * Coordinates passed in are LOCAL to the current page / scroll area. One unit = one pixel of book art.
 * Text never leaves its box: paragraphs wrap, single lines are fitted, and long pages scroll.
 */
final class Ui {
    record Hot(int x1, int y1, int x2, int y2, Runnable action) {}

    static final class Scroll {
        float offset;
        int contentH, viewH;
        int ax1, ay1, ax2, ay2;
        void wheel(double d) { offset = Mth.clamp(offset - (float) d * 10f, 0, Math.max(0, contentH - viewH)); }
    }

    static final int LH = 10;                 // paragraph line height

    final GuiGraphics g;
    final Font font;
    final double mx, my;                      // mouse in book space
    final float sx, sy, zoom;                 // where the book sits on screen
    final boolean record;
    final List<Hot> hots = new ArrayList<>();
    final List<Scroll> scrolls = new ArrayList<>();
    final long now = Util.getMillis();

    int ox, oy;
    private boolean clipped;
    private int cx1, cy1, cx2, cy2;
    private int sbX, sbY, sbW, sbH;
    List<Component> tip;
    ItemStack tipStack = ItemStack.EMPTY;

    Ui(GuiGraphics g, Font font, double mx, double my, float sx, float sy, float zoom, boolean record) {
        this.g = g; this.font = font; this.mx = mx; this.my = my; this.sx = sx; this.sy = sy; this.zoom = zoom; this.record = record;
    }

    // ------------------------------------------------------------------ spaces
    void begin(int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        ox = x; oy = y; clipped = false;
    }
    void end() { g.pose().popPose(); }
    void shift(int dx, int dy) { g.pose().translate(dx, dy, 0); ox += dx; oy += dy; }

    void scrollBegin(Scroll s, int x, int y, int w, int h) {
        s.viewH = h;
        s.ax1 = ox + x; s.ay1 = oy + y; s.ax2 = s.ax1 + w; s.ay2 = s.ay1 + h;
        if (record) scrolls.add(s);
        s.offset = Mth.clamp(s.offset, 0, Math.max(0, s.contentH - h));
        g.enableScissor((int) Math.floor(sx + s.ax1 * zoom), (int) Math.floor(sy + s.ay1 * zoom),
                (int) Math.ceil(sx + s.ax2 * zoom), (int) Math.ceil(sy + s.ay2 * zoom));
        g.pose().pushPose();
        g.pose().translate(x, y - (int) s.offset, 0);
        sbX = x; sbY = y; sbW = w; sbH = h;
        ox += x; oy += y - (int) s.offset;
        clipped = true; cx1 = s.ax1; cy1 = s.ay1; cx2 = s.ax2; cy2 = s.ay2;
    }

    void scrollEnd(Scroll s, int contentBottom) {
        g.pose().popPose();
        g.disableScissor();
        ox -= sbX; oy -= sbY - (int) s.offset;
        clipped = false;
        s.contentH = contentBottom + 3;
        if (s.contentH > s.viewH) {
            int trackX = sbX + sbW - 3;
            rect(g, trackX, sbY, 3, sbH, TAN);
            int th = Math.max(12, (int) (sbH * ((float) s.viewH / s.contentH)));
            int ty = sbY + (int) ((sbH - th) * (s.offset / Math.max(1, s.contentH - s.viewH)));
            rect(g, trackX, ty, 3, th, BROWN);
            rect(g, trackX, ty, 1, th, TAN_D);
        }
    }

    // ------------------------------------------------------------------ input
    boolean hover(int x, int y, int w, int h) {
        int x1 = ox + x, y1 = oy + y, x2 = x1 + w, y2 = y1 + h;
        if (mx < x1 || mx >= x2 || my < y1 || my >= y2) return false;
        return !clipped || (mx >= cx1 && mx < cx2 && my >= cy1 && my < cy2);
    }

    void hot(int x, int y, int w, int h, Runnable r) {
        if (!record) return;
        int x1 = ox + x, y1 = oy + y, x2 = x1 + w, y2 = y1 + h;
        if (clipped) {
            x1 = Math.max(x1, cx1); y1 = Math.max(y1, cy1); x2 = Math.min(x2, cx2); y2 = Math.min(y2, cy2);
            if (x2 <= x1 || y2 <= y1) return;
        }
        hots.add(new Hot(x1, y1, x2, y2, r));
    }

    void tipText(String... lines) {
        tip = new ArrayList<>();
        for (String l : lines) tip.add(Component.literal(l));
    }

    // ------------------------------------------------------------------ text
    int textWidth(String s) { return font.width(BookStyle.text(s)); }

    /** Single line, fitted to maxW (ends in "..." if it can't fit). */
    Component fit(String s, int maxW) {
        Component c = BookStyle.text(s);
        if (font.width(c) <= maxW) return c;
        String t = s;
        while (t.length() > 1 && font.width(BookStyle.text(t + "...")) > maxW) t = t.substring(0, t.length() - 1);
        return BookStyle.text(t + "...");
    }

    int text(String s, int x, int y, int color) {
        Component c = BookStyle.text(s);
        g.drawString(font, c, x, y, color, false);
        return font.width(c);
    }

    int textFit(String s, int x, int y, int maxW, int color) {
        Component c = fit(s, maxW);
        g.drawString(font, c, x, y, color, false);
        return font.width(c);
    }

    void textC(String s, int cx, int y, int color, int maxW) {
        Component c = fit(s, maxW);
        g.drawString(font, c, cx - font.width(c) / 2, y, color, false);
    }

    int paraH(String s, int w) { return font.split(BookStyle.text(s), w).size() * LH; }

    int para(String s, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(BookStyle.text(s), w);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y + i * LH, color, false);
        return lines.size() * LH;
    }

    /** Page heading: ornaments + title, scalloped divider underneath. Returns y where content may start. */
    int pageTitle(String t, int w, boolean cartouche) {
        int tw = Math.min(w - 36, textWidth(t));
        Component c = fit(t, w - 36);
        int cx = w / 2;
        if (cartouche) card(g, cx - tw / 2 - 6, 0, tw + 12, 13, TAN, 0);
        g.drawString(font, c, cx - tw / 2, 2, BROWN, false);
        int lineL = 9, lineR = cx - tw / 2 - (cartouche ? 9 : 4);
        if (lineR > lineL) { rect(g, lineL, 6, lineR - lineL, 1, TAN); }
        int rl = cx + tw / 2 + (cartouche ? 9 : 4), rr = w - 9;
        if (rr > rl) { rect(g, rl, 6, rr - rl, 1, TAN); }
        diamond(g, 4, 6, 4, TAN);
        diamond(g, w - 5, 6, 4, TAN);
        scallops(g, 0, 15, w, TAN);
        return 22;
    }

    // ------------------------------------------------------------------ widgets
    /** A tan card. Hovered cards lighten slightly. */
    boolean card(int x, int y, int w, int h) {
        boolean hv = hover(x, y, w, h);
        Pix.card(g, x, y, w, h, hv ? TAN_L : TAN, TAN_D);
        return hv;
    }

    /** Coloured button / chip (green, blue, red, purple like the reference). */
    boolean button(String label, int x, int y, int w, int h, int fill, int shade, Runnable action) {
        boolean hv = action != null && hover(x, y, w, h);
        Pix.card(g, x, y, w, h, hv ? lighten(fill) : fill, shade);
        Component c = fit(label, w - 4);
        g.drawString(font, c, x + (w - font.width(c)) / 2, y + (h - 8) / 2, WHITE, false);
        if (action != null) hot(x, y, w, h, action);
        return hv;
    }

    void chip(String label, int x, int y, int w, int h, int fill, int shade) {
        Pix.card(g, x, y, w, h, fill, shade);
        Component c = fit(label, w - 4);
        g.drawString(font, c, x + (w - font.width(c)) / 2, y + (h - 8) / 2, WHITE, false);
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 22), gr = Math.min(255, ((c >> 8) & 255) + 22), b = Math.min(255, (c & 255) + 22);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    void slot(int x, int y) {
        rect(g, x, y, 18, 18, TAN_D);
        rect(g, x + 1, y + 1, 16, 16, TAN);
    }

    void item(ItemStack s, int x, int y) {
        if (s.isEmpty()) return;
        g.renderItem(s, x, y);
        if (hover(x, y, 16, 16)) tipStack = s;
    }

    ItemStack cycle(List<ItemStack> stacks) {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get((int) ((now / 1000) % stacks.size()));
    }

    /** NPC face from the skin (head + hat layer), size must be a multiple of 8 to stay pixel-perfect. */
    void face(String npcId, int x, int y, int size) {
        ResourceLocation skin = NpcRenderer.texture(npcId);
        rect(g, x - 1, y - 1, size + 2, size + 2, DARK);
        RenderSystem.enableBlend();
        g.blit(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        g.blit(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
    }

    /** Heart (fills in quarters) + "3/8" chip, anchored at x. Returns the width used. */
    int heartChip(int x, int y, BookData.Npc n) {
        int max = n.maxHearts(), cur = n.hearts();
        String label = cur + "/" + max;
        int w = textWidth(label) + 8;
        boolean full = cur >= max;
        Pix.heart(g, x, y + 1, Pix.quartersOfCurrentHeart(n.points(), max));
        Pix.card(g, x + 11, y, w, 12, full ? GREEN : BLUE, full ? GREEN_D : BLUE_D);
        g.drawString(font, BookStyle.text(label), x + 15, y + 2, WHITE, false);
        if (hover(x, y, w + 11, 12)) {
            tipText("Friendship: " + n.points() + " points", "Each heart is 250 points.");
            if (n.romance() && n.status().equals("FRIEND")) tip.add(Component.literal("Dating opens hearts 9-10."));
            else if (n.romance() && !n.status().equals("MARRIED")) tip.add(Component.literal("Marriage opens hearts 11-14."));
        }
        return w + 11;
    }

    /** Row of hearts, one per heart of the CURRENT stage (8, then 10, then 14). */
    void heartsRow(int x, int y, BookData.Npc n) {
        int max = n.maxHearts();
        for (int i = 0; i < max; i++) {
            int rem = Mth.clamp(n.points() - i * 250, 0, 250);
            Pix.heart(g, x + i * 10, y, Math.min(4, rem * 4 / 250));
        }
    }
}
