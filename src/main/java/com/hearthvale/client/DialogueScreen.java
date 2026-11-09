package com.hearthvale.client;

import com.hearthvale.client.book.BookStyle;
import com.hearthvale.client.book.Pix;
import com.hearthvale.network.DialoguePagePayload;
import com.hearthvale.network.DialogueResponsePayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.hearthvale.client.book.Pix.*;

/**
 * Dialogue box in the same pixel style as the Hearthvale book: bordered cream panel, framed portrait, name tab,
 * tan choice cards, Game Paused font. Portrait art is the NPC's face for now (mood sprites can slot into drawPortrait later).
 * Long lines wrap; very long speeches scroll with the mouse wheel.
 */
public class DialogueScreen extends Screen {
    public final int entityId;
    private DialoguePagePayload page;
    private ResourceLocation skin;
    private List<String> lines = List.of();
    private int shown, scroll;
    private boolean awaiting;

    // layout of the last frame (art units) for hit-testing
    private float zoom = 1f;
    private final List<int[]> choiceRects = new ArrayList<>();
    private int[] panelRect = {0, 0, 0, 0};

    public DialogueScreen(DialoguePagePayload page) {
        super(Component.literal(page.speaker()));
        this.entityId = page.entityId();
        load(page);
    }

    private void load(DialoguePagePayload p) {
        this.page = p;
        this.skin = NpcRenderer.texture(p.npcId());
        this.shown = 0;
        this.scroll = 0;
        this.awaiting = false;
        this.lines = List.of();            // re-wrapped on the next frame
    }

    public void setPage(DialoguePagePayload p) { load(p); }

    private int points() { return page.points() & 0xFFFF; }
    private int maxHearts() { return page.points() >>> 16; }
    private boolean hasPortrait() { return !page.npcId().isEmpty(); }

    // ---------------------------------------------------------------- text wrapping (plain strings so the typewriter can reveal per character)
    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && font.width(BookStyle.text(trial)) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(trial);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ---------------------------------------------------------------- input
    private void choose(int idx) {
        if (awaiting) return;
        awaiting = true;
        PacketDistributor.sendToServer(new DialogueResponsePayload(idx));
    }

    private int totalChars() { int n = 0; for (String l : lines) n += l.length(); return n; }

    private void advance() {
        if (shown < totalChars()) { shown = totalChars(); return; }
        if (page.choices().isEmpty() && !awaiting) {
            awaiting = true;
            PacketDistributor.sendToServer(new DialogueResponsePayload(-1));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = mx / zoom, y = my / zoom;
        if (shown >= totalChars()) {
            for (int i = 0; i < choiceRects.size(); i++) {
                int[] r = choiceRects.get(i);
                if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) { choose(i); return true; }
            }
        }
        advance();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { advance(); return true; }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int idx = key - GLFW.GLFW_KEY_1;
            if (idx < page.choices().size() && shown >= totalChars()) { choose(idx); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new DialogueResponsePayload(-2));
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() { return !(ClientCutscene.active && !ClientCutscene.skippable); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        int total = totalChars();
        if (shown < total) shown = Math.min(total, shown + 2);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { /* keep the world visible */ }

    // ---------------------------------------------------------------- drawing
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        double gs = minecraft.getWindow().getGuiScale();
        zoom = (float) (Math.max(1, Math.floor(gs)) / gs);       // whole screen pixels per art pixel
        int uw = (int) (width / zoom), uh = (int) (height / zoom);
        g.pose().pushPose();
        g.pose().scale(zoom, zoom, 1f);

        int pw = Math.min(uw - 16, 330), portraitW = hasPortrait() ? 38 : 0;
        int textX0 = 10 + portraitW, textW = pw - textX0 - 12;
        if (lines.isEmpty()) lines = wrap(page.text(), textW);
        int maxLines = Math.max(3, Math.min(8, (int) ((uh * 0.30f - 20) / 10)));
        int visibleLines = Math.min(lines.size(), maxLines);
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - maxLines));
        int ph = Math.max(48, visibleLines * 10 + 24);
        int px = (uw - pw) / 2, py = uh - ph - 10;
        panelRect = new int[]{px, py, pw, ph};

        // name tab (+ friendship counter) sitting on the panel's top edge
        String name = page.speaker();
        Component nameC = BookStyle.text(name);
        int nameW = Math.min(pw / 2, font.width(nameC)) + 12;
        Pix.card(g, px + 8, py - 11, nameW, 13, TAN, TAN_D);
        g.drawString(font, fitText(name, nameW - 10), px + 13, py - 8, BookStyle.INK, false);
        Pix.panel(g, px, py, pw, ph);
        // re-draw the tab's lower edge over the panel border so it looks attached
        Pix.rect(g, px + 9, py, nameW - 2, 3, TAN);

        if (maxHearts() > 0) {
            int cur = Math.min(maxHearts(), points() / 250);
            String label = cur + "/" + maxHearts();
            int lw = font.width(BookStyle.text(label)) + 8;
            int hx = px + 8 + nameW + 4;
            Pix.heart(g, hx, py - 9, Pix.quartersOfCurrentHeart(points(), maxHearts()));
            boolean full = cur >= maxHearts();
            Pix.card(g, hx + 11, py - 10, lw, 12, full ? GREEN : BLUE, full ? GREEN_D : BLUE_D);
            g.drawString(font, BookStyle.text(label), hx + 15, py - 8, WHITE, false);
        }

        if (hasPortrait()) drawPortrait(g, px + 7, py + 8);

        // text with typewriter
        int remaining = shown;
        for (int i = 0; i < visibleLines; i++) {
            String line = lines.get(scroll + i);
            int upTo = Math.max(0, Math.min(line.length(), remaining - charsBefore(scroll + i)));
            if (upTo > 0) g.drawString(font, BookStyle.text(line.substring(0, upTo)), px + textX0, py + 9 + i * 10, BookStyle.INK, false);
        }
        if (lines.size() > maxLines) {                            // scroll hint
            if (scroll > 0) Pix.arrow(g, px + pw - 14, py + 6, 2, BROWN);
            if (scroll + maxLines < lines.size()) Pix.arrow(g, px + pw - 14, py + ph - 14, 2, BROWN);
        }
        boolean typed = shown >= totalChars();
        if (typed && page.choices().isEmpty() && (System.currentTimeMillis() / 450) % 2 == 0 && lines.size() <= maxLines) {
            Pix.arrow(g, px + pw - 14, py + ph - 12, 2, BROWN);
        }

        // choices: tan cards above the panel, right-aligned
        choiceRects.clear();
        int n = page.choices().size(), cy0 = py - 16 - n * 16;
        for (int i = 0; i < n; i++) {
            String label = (i + 1) + ". " + page.choices().get(i);
            int cw = Math.min(pw - 24, font.width(BookStyle.text(label)) + 16);
            int cx = px + pw - cw - 6, cy = cy0 + i * 16;
            choiceRects.add(new int[]{cx, cy, cw, 14});
            boolean hv = typed && mx / zoom >= cx && mx / zoom < cx + cw && my / zoom >= cy && my / zoom < cy + 14;
            Pix.card(g, cx, cy, cw, 14, hv ? TAN_L : TAN, TAN_D);
            g.drawString(font, fitText(label, cw - 10), cx + 5, cy + 3, typed ? BookStyle.INK : BookStyle.FAINT, false);
        }
        g.pose().popPose();
    }

    private int charsBefore(int line) { int n = 0; for (int i = 0; i < line; i++) n += lines.get(i).length(); return n; }

    private Component fitText(String s, int maxW) {
        Component c = BookStyle.text(s);
        if (font.width(c) <= maxW) return c;
        String t = s;
        while (t.length() > 1 && font.width(BookStyle.text(t + "...")) > maxW) t = t.substring(0, t.length() - 1);
        return BookStyle.text(t + "...");
    }

    /** Portrait slot. Currently the NPC's face; per-mood sprites will replace the body of this method. */
    private void drawPortrait(GuiGraphics g, int x, int y) {
        Pix.card(g, x, y, 30, 30, TAN, TAN_D);
        Pix.rect(g, x + 2, y + 2, 26, 26, DARK);
        RenderSystem.enableBlend();
        g.blit(skin, x + 3, y + 3, 24, 24, 8f, 8f, 8, 8, 64, 64);
        g.blit(skin, x + 3, y + 3, 24, 24, 40f, 8f, 8, 8, 64, 64);
    }
}
