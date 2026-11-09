package com.hearthvale.client.book;

import com.hearthvale.ClientConfig;
import com.hearthvale.network.RequestBookPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

import static com.hearthvale.client.book.Pix.*;

/**
 * The Hearthvale book - a pixel-art open book with page turns, section tabs on top and coloured bookmark flags on the right.
 * Everything is laid out in art pixels and drawn at a WHOLE number of screen pixels per art pixel, so it stays crisp at any GUI scale.
 * To add a bookmark later: add one {@link Bookmark} in the constructor (flag sprite + tooltip + action).
 */
public class BookScreen extends Screen {
    // ---- geometry (art pixels, root coordinates)
    static final int BOOK_W = 358, BOOK_H = 201, BOOK_Y = 19;
    static final int ROOT_W = 378, ROOT_H = BOOK_Y + BOOK_H + 2;
    static final int PX_L = 19, PX_R = 188, PY = BOOK_Y + 5, PW = 155, PH = 174, PAD = 5;
    static final int CW = PW - 2 * PAD, CH = PH - 2 * PAD;
    static final int TAB_X = 27, TAB_STEP = 28, TAB_W = 26, TAB_H = 17;
    static final int FLAG_X = 348, FLAG_Y = BOOK_Y + 32, FLAG_STEP = 21, FLAG_W = 25, FLAG_H = 17;
    private static final int FLIP_TICKS = 9, OPEN_TICKS = 6;

    record Bookmark(ResourceLocation sprite, String tooltip, Runnable action, BooleanSupplier active) {}
    record SectionTab(ResourceLocation sprite, ResourceLocation activeSprite, String tooltip, Runnable action, BooleanSupplier active) {}

    BookData data;
    final ItemEntries items;
    private final Deque<Views.View> stack = new ArrayDeque<>();
    private final List<Bookmark> bookmarks = new ArrayList<>();
    private final List<SectionTab> tabs = new ArrayList<>();

    private Views.View flipFrom, flipTo;
    private int flipDir, flipTicks, openTicks, age;
    private boolean flipping, swapped;
    private Runnable swap;

    private Ui lastUi;
    private float lastSx, lastSy, lastZoom = 1f;

    public BookScreen(BookData data) {
        super(Component.literal("Hearthvale"));
        this.data = data;
        this.items = ItemEntries.load();
        stack.push(new Views.MenuView());

        tabs.add(new SectionTab(BookStyle.tex("tab_villagers"), BookStyle.tex("tab_villagers_active"), "Villagers",
                () -> openSection(new Views.NpcListView()), () -> inStack(Views.NpcListView.class)));
        tabs.add(new SectionTab(BookStyle.tex("tab_items"), BookStyle.tex("tab_items_active"), "Items & Blocks",
                () -> openSection(new Views.ItemListView()), () -> inStack(Views.ItemListView.class)));

        // colour-coded bookmarks on the right edge - add new ones here
        bookmarks.add(new Bookmark(BookStyle.FLAG_HOME, "Front page", this::goHome, () -> stack.size() == 1));
        bookmarks.add(new Bookmark(BookStyle.FLAG_SETTINGS, "Settings", () -> openSection(new Views.SettingsView()), () -> inStack(Views.SettingsView.class)));
        bookmarks.add(new Bookmark(BookStyle.FLAG_CLOSE, "Close", this::onClose, () -> false));
    }

    public static void open(CompoundTag tag) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new BookScreen(BookData.parse(tag)));
    }

    public void update(CompoundTag tag) { this.data = BookData.parse(tag); }

    private boolean inStack(Class<?> c) {
        for (Views.View v : stack) if (c.isInstance(v)) return true;
        return false;
    }

    // ------------------------------------------------------------------ navigation
    private List<Views.View> path() {
        List<Views.View> p = new ArrayList<>(stack);
        Collections.reverse(p);                 // bottom (menu) first
        return p;
    }

    void openSection(Views.View root) {
        Views.View menu = path().get(0);
        navigate(new ArrayList<>(List.of(menu, root)), 1);
    }

    void openDetail(Views.View v) {
        List<Views.View> p = path();
        p.add(v);
        navigate(p, 1);
    }

    void pop() {
        List<Views.View> p = path();
        if (p.size() <= 1) return;
        p.remove(p.size() - 1);
        navigate(p, -1);
    }

    void goHome() {
        if (stack.size() <= 1) return;
        navigate(new ArrayList<>(List.of(path().get(0))), -1);
    }

    private void navigate(List<Views.View> target, int dir) {
        if (flipping) return;
        for (int i = 0; i < target.size(); i++) target.get(i).depth = i;
        Runnable apply = () -> {
            stack.clear();
            for (Views.View v : target) stack.push(v);
        };
        if (!ClientConfig.PAGE_TURN.get()) { apply.run(); return; }
        flipFrom = stack.peek();
        flipTo = target.get(target.size() - 1);
        flipDir = dir;
        flipTicks = 0;
        flipping = true;
        swapped = false;
        swap = apply;
    }

    @Override
    public void tick() {
        age++;
        if (openTicks < OPEN_TICKS) openTicks++;
        if (flipping) {
            flipTicks++;
            if (!swapped && flipTicks >= FLIP_TICKS / 2) { swap.run(); swapped = true; }
            if (flipTicks >= FLIP_TICKS) {
                if (!swapped) swap.run();
                flipping = false;
            }
        } else if (age % 60 == 0) {
            PacketDistributor.sendToServer(new RequestBookPayload(false));
        }
    }

    // ------------------------------------------------------------------ sizing: whole screen pixels per art pixel
    private int pixelScale() {
        var win = minecraft.getWindow();
        int fit = (int) Math.floor(Math.min(0.94 * win.getWidth() / ROOT_W, 0.92 * win.getHeight() / ROOT_H));
        fit = Math.max(1, fit);
        ClientConfig.UiSize size = ClientConfig.UI_SIZE.get();
        return size == ClientConfig.UiSize.AUTO ? fit : Math.min(size.pixels, fit);
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x88150D10, 0xA0150D10);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        var win = minecraft.getWindow();
        double gs = win.getGuiScale();
        int k = pixelScale();
        float zoom = (float) (k / gs);
        float sx = (float) (Math.round((win.getWidth() - ROOT_W * k) / 2.0) / gs);
        float sy = (float) (Math.round((win.getHeight() - ROOT_H * k) / 2.0) / gs);
        lastSx = sx; lastSy = sy; lastZoom = zoom;

        float open = Math.min(1f, (openTicks + pt) / OPEN_TICKS);
        boolean interactive = open >= 1f && !flipping;
        int slide = Math.round((1f - open) * (1f - open) * 14f);

        Ui ui = new Ui(g, font, (mx - sx) / zoom, (my - sy) / zoom, sx, sy, zoom, interactive);
        g.pose().pushPose();
        g.pose().translate(sx, sy, 0);
        g.pose().scale(zoom, zoom, 1f);
        g.pose().translate(0, slide, 0);
        ui.begin(0, 0);

        for (int i = 0; i < tabs.size(); i++) {                 // section tabs peek out from behind the pages
            SectionTab t = tabs.get(i);
            int x = TAB_X + i * TAB_STEP;
            boolean hv = ui.hover(x, 2, TAB_W, 19), act = t.active().getAsBoolean();
            int y = act || hv ? 2 : 4;
            g.blit(act ? t.activeSprite() : t.sprite(), x, y, 0f, 0f, TAB_W, TAB_H, TAB_W, TAB_H);
            ui.hot(x, 2, TAB_W, 19, t.action());
            if (hv) ui.tipText(t.tooltip());
        }

        g.blit(BookStyle.BOOK, 0, BOOK_Y, 0f, 0f, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
        pages(ui, pt);

        for (int i = 0; i < bookmarks.size(); i++) {            // coloured bookmarks over the right cover edge
            Bookmark b = bookmarks.get(i);
            int y = FLAG_Y + i * FLAG_STEP;
            boolean hv = ui.hover(FLAG_X, y, FLAG_W + 6, FLAG_H);
            int x = FLAG_X + (b.active().getAsBoolean() ? 3 : 0) + (hv ? 3 : 0);
            g.blit(b.sprite(), x, y, 0f, 0f, FLAG_W, FLAG_H, FLAG_W, FLAG_H);
            ui.hot(FLAG_X, y, FLAG_W + 6, FLAG_H, b.action());
            if (hv) ui.tipText(b.tooltip());
        }
        ui.end();
        g.pose().popPose();
        lastUi = interactive ? ui : null;

        if (!ui.tipStack.isEmpty()) g.renderTooltip(font, ui.tipStack, mx, my);
        else if (ui.tip != null) g.renderComponentTooltip(font, ui.tip, mx, my);
    }

    private void pages(Ui ui, float pt) {
        Views.View cur = stack.peek();
        if (!flipping) {
            page(ui, true, cur, 1f);
            page(ui, false, cur, 1f);
            return;
        }
        float p = Math.min(1f, (flipTicks + pt) / FLIP_TICKS);
        float c = (float) Math.cos(Math.PI * p);
        boolean first = p < 0.5f;
        if (flipDir > 0) {                    // forward: the right page lifts and turns over onto the left
            page(ui, true, flipFrom, 1f);
            page(ui, false, flipTo, 1f);
            if (first) page(ui, false, flipFrom, Math.max(0.03f, c)); else page(ui, true, flipTo, Math.max(0.03f, -c));
        } else {                              // back: the left page lifts and turns over onto the right
            page(ui, true, flipTo, 1f);
            page(ui, false, flipFrom, 1f);
            if (first) page(ui, true, flipFrom, Math.max(0.03f, c)); else page(ui, false, flipTo, Math.max(0.03f, -c));
        }
    }

    /** scaleX < 1 draws a page mid-turn, hinged at the spine. */
    private void page(Ui ui, boolean left, Views.View v, float scaleX) {
        GuiGraphics g = ui.g;
        boolean turning = scaleX != 1f;
        int bx = left ? PX_L : PX_R;
        if (turning) {
            g.pose().pushPose();
            if (left) {
                g.pose().translate(bx + PW, 0, 0);
                g.pose().scale(scaleX, 1f, 1f);
                g.pose().translate(-PW, 0, 0);
            } else {
                g.pose().translate(bx, 0, 0);
                g.pose().scale(scaleX, 1f, 1f);
            }
            bx = 0;
            rect(g, bx, PY, PW, PH, CREAM);                              // the turning sheet
        }
        for (int[] c : new int[][]{{2, 2}, {PW - 4, 2}, {2, PH - 4}, {PW - 4, PH - 4}}) rect(g, bx + c[0], PY + c[1], 2, 2, TAN);   // tiny corner marks

        ui.begin(bx + PAD, PY + PAD);
        int top = 0;
        if (left && v.depth > 0) {
            boolean hv = ui.hover(0, 0, 36, 12);
            Pix.card(g, 0, 0, 36, 12, hv ? TAN_L : TAN, TAN_D);
            Pix.arrow(g, 5, 2, 0, BROWN);
            ui.text("Back", 14, 2, BookStyle.INK);
            ui.hot(0, 0, 36, 12, this::pop);
            top = 16;
        }
        ui.shift(0, top);
        if (left) v.left(ui, this, CW, CH - top); else v.right(ui, this, CW, CH);
        ui.end();
        if (turning) {
            int shade = (int) ((1f - scaleX) * 0x55);
            g.fill(bx, PY, bx + PW, PY + PH, (shade << 24) | 0x341C27);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (lastUi == null) return true;
        if (button == 1) { pop(); return true; }
        double vx = (mx - lastSx) / lastZoom, vy = (my - lastSy) / lastZoom;
        for (int i = lastUi.hots.size() - 1; i >= 0; i--) {
            Ui.Hot h = lastUi.hots.get(i);
            if (vx >= h.x1() && vx < h.x2() && vy >= h.y1() && vy < h.y2()) { h.action().run(); return true; }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (lastUi == null) return true;
        double vx = (mx - lastSx) / lastZoom, vy = (my - lastSy) / lastZoom;
        for (Ui.Scroll s : lastUi.scrolls) {
            if (vx >= s.ax1 && vx < s.ax2 && vy >= s.ay1 && vy < s.ay2) { s.wheel(sy); return true; }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (BookKeys.OPEN.matches(key, scan)) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) { pop(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
