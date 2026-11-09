package com.hearthvale.client.book;

import com.hearthvale.ClientConfig;
import com.hearthvale.ModRegistry;
import com.hearthvale.network.SettingsPayload;
import com.hearthvale.util.GameTime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

import static com.hearthvale.client.book.BookStyle.*;
import static com.hearthvale.client.book.Pix.*;

/** Every page of the book. A view fills a left and a right page (content area w x h, origin top-left). */
final class Views {
    private Views() {}

    abstract static class View {
        int depth;
        abstract void left(Ui ui, BookScreen s, int w, int h);
        abstract void right(Ui ui, BookScreen s, int w, int h);
    }

    /** Small heading inside a page: diamond bullet + title. */
    static int section(Ui ui, String t, int x, int y, int maxW) {
        diamond(ui.g, x + 2, y + 4, 2, TAN_D);
        ui.textFit(t, x + 8, y, maxW - 8, INK);
        return y + 12;
    }

    // =====================================================================================  MENU
    static final class MenuView extends View {
        @Override
        void left(Ui ui, BookScreen s, int w, int h) {
            int x = (w - 128) / 2, y = Math.max(0, (h - 128) / 2 - 6);
            ui.g.blit(TREE, x, y, 128, 128, 0f, 0f, 128, 128, 128, 128);
            int sx0 = x + (int) (128 * 0.22f), sx1 = x + (int) (128 * 0.78f), sy0 = y + (int) (128 * 0.60f), sy1 = y + (int) (128 * 0.80f);
            var c = text("Hearthvale");
            float sc = Math.min(1f, (sx1 - sx0 - 6) / (float) ui.font.width(c));
            float tx = sx0 + ((sx1 - sx0) - ui.font.width(c) * sc) / 2f, ty = sy0 + ((sy1 - sy0) - 8 * sc) / 2f;
            for (int pass = 0; pass < 2; pass++) {
                ui.g.pose().pushPose();
                ui.g.pose().translate(tx + (pass == 0 ? 1 : 0), ty + (pass == 0 ? 1 : 0), 0);
                ui.g.pose().scale(sc, sc, 1f);
                ui.g.drawString(ui.font, c, 0, 0, pass == 0 ? MAROON : WHITE, false);
                ui.g.pose().popPose();
            }
        }

        @Override
        void right(Ui ui, BookScreen s, int w, int h) {
            int y = ui.pageTitle("Contents", w, true) + 4;
            entry(ui, y, w, "Villagers", "Friendship, homes and events", () -> {
                ui.g.pose().pushPose();
                ui.g.pose().translate(8, y + 11, 0);
                ui.g.pose().scale(2f, 2f, 1f);
                Pix.heart(ui.g, 0, 0, 4);
                ui.g.pose().popPose();
            }, () -> s.openSection(new NpcListView()));
            entry(ui, y + 44, w, "Items & Blocks", "Recipes and how to use them",
                    () -> ui.g.renderItem(new ItemStack(ModRegistry.HEARTH_ITEM.get()), 7, y + 44 + 12), () -> s.openSection(new ItemListView()));

            scallops(ui.g, 0, h - 42, w, TAN);
            ui.textFit(dateLabel(), 0, h - 32, w, SOFT);
            ui.textFit("Villagers met: " + s.data.metCount() + " / " + s.data.npcs.size(), 0, h - 20, w, SOFT);
        }

        private static void entry(Ui ui, int y, int w, String title, String sub, Runnable icon, Runnable action) {
            ui.card(0, y, w, 40);
            icon.run();
            ui.textFit(title, 30, y + 6, w - 34, INK);
            ui.para(sub, 30, y + 18, w - 34, SOFT);          // wraps inside the card
            ui.hot(0, y, w, 40, action);
        }

        private static String dateLabel() {
            var level = Minecraft.getInstance().level;
            if (level == null) return "";
            long day = level.getDayTime() / 24000L;
            return GameTime.capitalize(GameTime.SEASONS[(int) ((day / 28) % 4)]) + " " + ((day % 28) + 1) + " - " + GameTime.capitalize(GameTime.WEEKDAYS[(int) (day % 7)]);
        }
    }

    // =====================================================================================  VILLAGER LIST
    static String statusLabel(BookData.Npc n) {
        if (!n.met()) return "Not met yet";
        return switch (n.status()) {
            case "DATING" -> "Dating";
            case "ENGAGED" -> "Engaged";
            case "MARRIED" -> "Married";
            default -> "Friend";
        };
    }

    static final class NpcListView extends View {
        private final Ui.Scroll scroll = new Ui.Scroll();
        private String expanded = "";

        @Override
        void left(Ui ui, BookScreen s, int w, int h) {
            int y0 = ui.pageTitle("Villagers", w, false);
            ui.scrollBegin(scroll, 0, y0, w, h - y0);
            int rw = w - 6, cy = 0;
            for (BookData.Npc n : s.data.npcs) {
                boolean open = n.id().equals(expanded);
                String sum = n.summary().isEmpty() ? "No description yet." : n.summary();
                int nameH = ui.paraH(n.name(), rw - 26 - 14);
                int headH = Math.max(30, 4 + nameH + 2 + 12 + 4);
                int bodyH = open ? ui.paraH(sum, rw - 12) + 24 : 0;
                int rowH = headH + bodyH;
                boolean hv = ui.hover(0, cy, rw, headH);
                Pix.card(ui.g, 0, cy, rw, rowH, hv ? TAN_L : TAN, TAN_D);
                ui.face(n.id(), 5, cy + (headH - 18) / 2 + 1, 16);
                ui.para(n.name(), 26, cy + 4, rw - 26 - 14, INK);
                int chipY = cy + 4 + nameH + 2;
                int used = ui.heartChip(26, chipY, n);
                int free = rw - 5 - (26 + used + 3);
                if (!n.met()) {
                    if (ui.textWidth("Not met") <= free) ui.text("Not met", rw - 5 - ui.textWidth("Not met"), chipY + 2, FAINT);
                } else if (!n.status().equals("FRIEND")) {
                    String st = statusLabel(n);
                    int cwid = ui.textWidth(st) + 8;
                    if (cwid <= free) ui.chip(st, rw - 5 - cwid, chipY, cwid, 12, n.status().equals("MARRIED") ? PURPLE : RED, n.status().equals("MARRIED") ? PURPLE_D : RED_D);
                }
                Pix.arrow(ui.g, rw - 10, cy + 8, open ? 2 : 1, BROWN);
                ui.hot(0, cy, rw, headH, () -> expanded = open ? "" : n.id());
                if (open) {
                    Pix.rect(ui.g, 3, cy + headH + 1, rw - 6, bodyH - 6, CREAM);
                    int ph = ui.para(sum, 6, cy + headH + 4, rw - 12, INK);
                    ui.button("Open profile", 6, cy + headH + 6 + ph, 72, 13, GREEN, GREEN_D, () -> s.openDetail(new NpcDetailView(n.id())));
                }
                cy += rowH + 3;
            }
            ui.scrollEnd(scroll, cy);
        }

        @Override
        void right(Ui ui, BookScreen s, int w, int h) {
            int y0 = ui.pageTitle("Friendship", w, true);
            ui.scrollBegin(rscroll, 0, y0, w, h - y0);
            int cw = w - 6, y = 2;
            y += ui.para("Each heart is worth 250 points. The counter grows as you get closer.", 0, y, cw, INK) + 4;
            y = section(ui, "Stages", 0, y, cw);
            String[][] st = {{"0/8", "Getting to know", "BLUE"}, {"8/10", "Dating", "BLUE"}, {"10/14", "Married", "BLUE"}};
            for (String[] r : st) {
                ui.chip(r[0], 0, y, 32, 12, BLUE, BLUE_D);
                ui.textFit(r[1], 38, y + 2, cw - 38, SOFT);
                y += 15;
            }
            y += 2;
            y += ui.para("Villagers that cannot be romanced go up to 10 hearts.", 0, y, cw, FAINT) + 5;
            y = section(ui, "Growing closer", 0, y, cw);
            String[] tips = {"Talk once a day: +20", "Gifts (sneak + right-click): loved +80, liked +45, any gift +20", "Hug someone you are dating (sneak + empty hand): +10 a day", "Walk up to people to start their heart events"};
            for (String t : tips) {
                bullet(ui.g, 0, y + 3, TAN_D);
                y += ui.para(t, 8, y, cw - 8, SOFT) + 3;
            }
            y += 3;
            y = section(ui, "Romance", 0, y, cw);
            y += ui.para("Dating needs 8 hearts and a Bouquet. Marriage needs 10 hearts and a Promise Pendant.", 0, y, cw, SOFT);
            ui.scrollEnd(rscroll, y);
        }

        private final Ui.Scroll rscroll = new Ui.Scroll();
    }

    // =====================================================================================  VILLAGER PROFILE
    static final class NpcDetailView extends View {
        private final String id;
        private final Ui.Scroll sl = new Ui.Scroll(), sr = new Ui.Scroll();

        NpcDetailView(String id) { this.id = id; }

        @Override
        void left(Ui ui, BookScreen s, int w, int h) {
            BookData.Npc n = s.data.npc(id);
            if (n == null) { ui.para("This villager is no longer available.", 0, 0, w, SOFT); return; }
            ui.face(n.id(), 2, 2, 24);
            int nh = ui.para(n.name(), 32, 2, w - 32, INK);
            int fill, shade;
            if (!n.met()) { fill = TAN_D; shade = BROWN; }
            else switch (n.status()) {
                case "DATING", "ENGAGED" -> { fill = RED; shade = RED_D; }
                case "MARRIED" -> { fill = PURPLE; shade = PURPLE_D; }
                default -> { fill = BLUE; shade = BLUE_D; }
            }
            int chipY = 2 + nh + 1;
            ui.chip(statusLabel(n), 32, chipY, Math.max(44, ui.textWidth(statusLabel(n)) + 10), 11, fill, shade);
            int bh = ui.para("Birthday: " + n.birthday(), 32, chipY + 13, w - 32, SOFT);
            int hy = Math.max(32, chipY + 13 + bh + 3);

            ui.heartsRow(0, hy, n);
            if (ui.hover(0, hy, n.maxHearts() * 10, 9)) ui.tipText("Friendship: " + n.points() + " points", "Each heart is 250 points.");
            ui.textFit("Friendship " + n.hearts() + "/" + n.maxHearts(), 0, hy + 12, w, SOFT);
            int dy = hy + 26;
            Pix.divider(ui.g, 0, dy, w);

            int sy0 = dy + 5;
            ui.scrollBegin(sl, 0, sy0, w, h - sy0);            int cw = w - 6, y = 0;
            y = section(ui, "Summary", 0, y, cw);
            y += ui.para(n.summary().isEmpty() ? "No description yet." : n.summary(), 0, y, cw, INK) + 6;
            if (!n.backstory().isEmpty()) {
                y = section(ui, "About", 0, y, cw);
                y += ui.para(n.backstory(), 0, y, cw, INK);
            }
            ui.scrollEnd(sl, y);
        }

        @Override
        void right(Ui ui, BookScreen s, int w, int h) {
            BookData.Npc n = s.data.npc(id);
            if (n == null) return;
            int y0 = ui.pageTitle("Home & Events", w, true);
            ui.scrollBegin(sr, 0, y0, w, h - y0);
            int cw = w - 6, y = 0;

            List<String> need = new ArrayList<>();
            if (!n.home().isEmpty()) need.add(nameOf(s, n.home()));
            for (String r : n.requires()) if (!need.contains(nameOf(s, r))) need.add(nameOf(s, r));
            if (!n.work().isEmpty() && !need.contains(nameOf(s, n.work()))) need.add(nameOf(s, n.work()));
            y += ui.para("Moves in once your village has: " + String.join(", ", need) + ". Build each room, then stand inside and use a Surveyor's Deed.", 0, y, cw, FAINT) + 4;

            Set<String> done = new HashSet<>();
            if (!n.home().isEmpty()) { y = building(ui, s, n.home(), "Home", y, cw); done.add(n.home()); }
            if (!n.work().isEmpty() && done.add(n.work())) y = building(ui, s, n.work(), "Workplace", y, cw);
            for (String r : n.requires()) if (done.add(r)) y = building(ui, s, r, "Also needed", y, cw);

            y = section(ui, "Events", 0, y + 2, cw);
            if (n.events().isEmpty()) y += ui.para("No events yet.", 0, y, cw, SOFT);
            for (BookData.Event ev : n.events()) {
                int tw = cw - (ev.seen() ? 26 : 12);
                int nh = ui.paraH(ev.name(), tw);
                int ch = Math.max(15, nh + 6);
                ui.card(0, y, cw, ch);
                ui.para(ev.name(), 5, y + 3, tw, INK);
                if (ev.seen()) Pix.check(ui.g, cw - 12, y + (ch - 6) / 2, GREEN_D);
                ui.hot(0, y, cw, ch, () -> s.openDetail(new EventView(id, ev.id())));
                y += ch + 3;
            }
            ui.scrollEnd(sr, y);
        }

        private static String nameOf(BookScreen s, String buildingId) {
            BookData.Building b = s.data.buildings.get(buildingId);
            return b == null ? buildingId.substring(buildingId.indexOf(':') + 1) : b.name();
        }

        private static int building(Ui ui, BookScreen s, String buildingId, String role, int y, int cw) {
            BookData.Building b = s.data.buildings.get(buildingId);
            if (b == null) return y;
            y = section(ui, role + ": " + b.name(), 0, y, cw);
            ui.textFit("At least " + b.minVolume() + " open blocks inside", 8, y - 2, cw - 8, FAINT);
            y += 11;
            for (BookData.Req r : b.reqs()) {
                List<ItemStack> stacks = stacksFor(r.any());
                String label = labelFor(r.any());
                int lh = ui.paraH(label, cw - 46);
                int rowH = Math.max(20, lh + 4);
                ui.slot(0, y);
                ui.item(ui.cycle(stacks), 1, y + 1);
                if (ui.hover(0, y, 18, 18)) {
                    ui.tipStack = ItemStack.EMPTY;
                    ui.tipText(label, r.count() + "x needed");
                }
                ui.text(r.count() + "X", 21, y + 5, INK);
                ui.para(label, 44, y + (rowH - lh) / 2, cw - 44, SOFT);
                y += rowH + 2;
            }
            return y + 4;
        }
    }

    // =====================================================================================  EVENT
    static final class EventView extends View {
        private final String npcId, eventId;
        private final Ui.Scroll sl = new Ui.Scroll(), sr = new Ui.Scroll();

        EventView(String npcId, String eventId) { this.npcId = npcId; this.eventId = eventId; }

        private BookData.Event find(BookData.Npc n) {
            for (BookData.Event e : n.events()) if (e.id().equals(eventId)) return e;
            return null;
        }

        @Override
        void left(Ui ui, BookScreen s, int w, int h) {
            BookData.Npc n = s.data.npc(npcId);
            BookData.Event ev = n == null ? null : find(n);
            if (ev == null) { ui.para("This event is no longer available.", 0, 0, w, SOFT); return; }
            int y = ui.pageTitle("Event", w, false);
            y += ui.para(ev.name(), 0, y, w - 4, INK) + 3;
            ui.face(n.id(), 2, y + 2, 16);
            ui.text("Villager", 24, y, FAINT);
            ui.textFit(n.name(), 24, y + 10, w - 24, INK);
            y += 26;
            ui.scrollBegin(sl, 0, y, w, h - y);
            int cw = w - 6, cy = 0;
            cy = section(ui, "When it starts", 0, cy, cw);
            cy += ui.para(ev.approach() ? "Plays when you walk up to " + n.name() + "." : "Plays when you talk to " + n.name() + ".", 0, cy, cw, INK) + 6;
            cy = section(ui, "Status", 0, cy, cw);
            if (ev.seen()) { Pix.check(ui.g, 1, cy + 1, GREEN_D); ui.text("Seen", 12, cy, GREEN_D); }
            else ui.text("Not seen yet", 0, cy, SOFT);
            cy += 14;
            cy += ui.para("Each event plays once.", 0, cy, cw, FAINT);
            ui.scrollEnd(sl, cy);
        }

        @Override
        void right(Ui ui, BookScreen s, int w, int h) {
            BookData.Npc n = s.data.npc(npcId);
            BookData.Event ev = n == null ? null : find(n);
            if (ev == null) return;
            int y0 = ui.pageTitle("Requirements", w, true);
            ui.scrollBegin(sr, 0, y0, w, h - y0);
            int cw = w - 6, y = 2;
            if (ev.reqs().isEmpty()) y += ui.para("No special requirements. It can play as soon as you meet " + n.name() + ".", 0, y, cw, INK);
            for (String line : ev.reqs()) {
                bullet(ui.g, 0, y + 3, TAN_D);
                y += ui.para(line, 8, y, cw - 8, INK) + 4;
            }
            ui.scrollEnd(sr, y);
        }
    }

    // =====================================================================================  ITEM LIST
    static final class ItemListView extends View {
        private final Ui.Scroll scroll = new Ui.Scroll(), rs = new Ui.Scroll();

        @Override
        void left(Ui ui, BookScreen s, int w, int h) {
            int y0 = ui.pageTitle("Items & Blocks", w, false);
            ui.scrollBegin(scroll, 0, y0, w, h - y0);
            int cy = 0, rw = w - 6;
            for (ItemEntries.Entry e : s.items.entries) {
                ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(e.item()));
                ui.card(0, cy, rw, 26);
                ui.slot(4, cy + 4);
                ui.item(st, 5, cy + 5);
                ui.textFit(st.getHoverName().getString(), 26, cy + 4, rw - 40, INK);
                ui.textFit(e.kind(), 26, cy + 14, rw - 40, SOFT);
                Pix.arrow(ui.g, rw - 10, cy + 10, 1, BROWN);
                ui.hot(0, cy, rw, 26, () -> s.openDetail(new ItemDetailView(e)));
                cy += 29;
            }
            ui.scrollEnd(scroll, cy);
        }

        @Override
        void right(Ui ui, BookScreen s, int w, int h) {
            int y0 = ui.pageTitle("Step by Step", w, true);
            ui.scrollBegin(rs, 0, y0, w, h - y0);
            int cw = w - 6, y = 2, n = 1;
            for (String step : s.items.intro) {
                int ph = ui.paraH(step, cw - 18);
                ui.chip(String.valueOf(n++), 0, y, 12, 12, BROWN, BROWN_D);
                ui.para(step, 17, y + 1, cw - 17, INK);
                y += Math.max(15, ph + 5);
            }
            ui.scrollEnd(rs, y);
        }
    }

    // =====================================================================================  ITEM PAGE
    static final class ItemDetailView extends View {
        private final ItemEntries.Entry entry;
        private final Ui.Scroll sl = new Ui.Scroll(), sr = new Ui.Scroll();

        ItemDetailView(ItemEntries.Entry e) { this.entry = e; }

        @Override
        void left(Ui ui, BookScreen s, int w, int h) {
            ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(entry.item()));
            ui.slot(2, 2);
            ui.item(st, 3, 3);
            ui.textFit(st.getHoverName().getString(), 26, 4, w - 26, INK);
            ui.textFit(entry.kind(), 26, 15, w - 26, SOFT);
            Pix.divider(ui.g, 0, 26, w);
            ui.scrollBegin(sl, 0, 31, w, h - 31);
            int cw = w - 6, y = 0;
            y = section(ui, "About", 0, y, cw);
            for (String p : entry.description()) y += ui.para(p, 0, y, cw, INK) + 4;
            if (!entry.usage().isEmpty()) {
                y = section(ui, "How to use", 0, y + 2, cw);
                for (String u : entry.usage()) {
                    bullet(ui.g, 0, y + 3, TAN_D);
                    y += ui.para(u, 8, y, cw - 8, SOFT) + 3;
                }
            }
            ui.scrollEnd(sl, y);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        void right(Ui ui, BookScreen s, int w, int h) {
            int y0 = ui.pageTitle("Crafting", w, true);
            var level = Minecraft.getInstance().level;
            Optional<RecipeHolder<?>> holder = entry.recipe() == null || level == null ? Optional.empty() : level.getRecipeManager().byKey(entry.recipe());
            ui.scrollBegin(sr, 0, y0, w, h - y0);
            int cw = w - 6, y = 6;
            if (holder.isEmpty()) {
                y += ui.para("This item can't be crafted. Find it in the creative inventory or ask an admin.", 0, y, cw, SOFT);
                ui.scrollEnd(sr, y);
                return;
            }
            Recipe<?> r = holder.get().value();
            int gw = 3, gh = 3;
            if (r instanceof ShapedRecipe sh) { gw = sh.getWidth(); gh = sh.getHeight(); }
            List<Ingredient> ing = new ArrayList<>(r.getIngredients());

            int panelW = 6 + 54 + 8 + 14 + 8 + 20 + 6, px = (cw - panelW) / 2;
            Pix.card(ui.g, px, y, panelW, 66, TAN, TAN_D);
            int gx = px + 6, gy = y + 6;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int sx = gx + col * 18, sy = gy + row * 18;
                    ui.slot(sx, sy);
                    if (col < gw && row < gh) {
                        int idx = row * gw + col;
                        if (idx < ing.size() && !ing.get(idx).isEmpty()) ui.item(ui.cycle(Arrays.asList(ing.get(idx).getItems())), sx + 1, sy + 1);
                    }
                }
            }
            int ax = gx + 54 + 8, ay = gy + 25;
            Pix.rect(ui.g, ax, ay + 2, 8, 3, BROWN);
            Pix.arrow(ui.g, ax + 8, ay, 1, BROWN);
            int rx = ax + 14 + 8, ry = gy + 18;
            ui.slot(rx, ry);
            ItemStack result = r.getResultItem(level.registryAccess());
            ui.item(result, rx + 1, ry + 1);
            y += 66 + 8;
            if (result.getCount() > 1) ui.text("Makes " + result.getCount(), 0, y, SOFT);
            y += 4;
            ui.textC(r instanceof ShapedRecipe ? "Shaped recipe" : "Shapeless: any arrangement", cw / 2, y, SOFT, cw);
            y += 14;
            y += ui.para("Items that cycle can be swapped for any matching one.", 0, y, cw, FAINT);
            ui.scrollEnd(sr, y);
        }
    }

    // =====================================================================================  SETTINGS
    static final class SettingsView extends View {
        private final Ui.Scroll sl = new Ui.Scroll(), sr = new Ui.Scroll();

        @Override
        void left(Ui ui, BookScreen s, int w, int h) {
            int y0 = ui.pageTitle("Interface", w, false);
            ui.scrollBegin(sl, 0, y0, w, h - y0);
            int cw = w - 6, y = 2;
            ClientConfig.UiSize size = ClientConfig.UI_SIZE.get();
            y = stepper(ui, "Book size", size.label(), y, cw, true, () -> setSize(size.prev()), () -> setSize(size.next())) + 10;
            y = toggle(ui, "Page turn animation", ClientConfig.PAGE_TURN.get(), y, cw, true, () -> {
                ClientConfig.PAGE_TURN.set(!ClientConfig.PAGE_TURN.get());
                ClientConfig.SPEC.save();
            }) + 10;
            y += ui.para("Auto picks the biggest size that fits your screen and GUI scale. These settings only affect this computer.", 0, y, cw, FAINT);
            ui.scrollEnd(sl, y);
        }

        private static void setSize(ClientConfig.UiSize v) {
            ClientConfig.UI_SIZE.set(v);
            ClientConfig.SPEC.save();
        }

        @Override
        void right(Ui ui, BookScreen s, int w, int h) {
            int y0 = ui.pageTitle("Village", w, true);
            BookData.Settings st = s.data.settings;
            ui.scrollBegin(sr, 0, y0, w, h - y0);
            int cw = w - 6, y = 4;
            boolean op = st.op();
            y = stepper(ui, "Traveller arrival time (minutes)", String.valueOf(st.arrivalMinutes()), y, cw, op,
                    () -> send("arrivalMinutes", st.arrivalMinutes() - 1), () -> send("arrivalMinutes", st.arrivalMinutes() + 1)) + 10;
            y = toggle(ui, "Heart events start by themselves", st.autoEvents(), y, cw, op, () -> send("autoEvents", st.autoEvents() ? 0 : 1)) + 10;
            y = stepper(ui, "Largest room size (blocks)", String.valueOf(st.maxVolume()), y, cw, op,
                    () -> send("maxVolume", st.maxVolume() - 100), () -> send("maxVolume", st.maxVolume() + 100)) + 10;
            y += ui.para(op ? "Applies to this world. Arrival is how often a new traveller may show up."
                    : "Only operators can change village settings.", 0, y, cw, FAINT);
            ui.scrollEnd(sr, y);
        }

        private static void send(String key, int value) {
            PacketDistributor.sendToServer(new SettingsPayload(key, value));
        }

        /** label (wraps), then [-] value [+] on the next line. */
        private static int stepper(Ui ui, String label, String value, int y, int w, boolean enabled, Runnable minus, Runnable plus) {
            int lh = ui.para(label, 0, y, w - 4, INK);
            int cy = y + lh + 1, x0 = w - 78;
            ui.button("-", x0, cy, 12, 12, enabled ? GREEN : TAN_D, enabled ? GREEN_D : BROWN, enabled ? minus : null);
            Pix.card(ui.g, x0 + 14, cy, 46, 12, TAN, TAN_D);
            ui.textC(value, x0 + 14 + 23, cy + 2, enabled ? INK : FAINT, 42);
            ui.button("+", x0 + 62, cy, 12, 12, enabled ? GREEN : TAN_D, enabled ? GREEN_D : BROWN, enabled ? plus : null);
            return cy + 12;
        }

        private static int toggle(Ui ui, String label, boolean on, int y, int w, boolean enabled, Runnable flip) {
            int lh = ui.para(label, 0, y, w - 4, INK);
            int cy = y + lh + 1;
            ui.button(on ? "On" : "Off", w - 34, cy, 30, 12, on ? GREEN : RED, on ? GREEN_D : RED_D, enabled ? flip : null);
            return cy + 12;
        }
    }

    // =====================================================================================  block helpers
    static String labelFor(List<String> any) {
        List<String> names = new ArrayList<>();
        for (String e : any) {
            String id = e.startsWith("#") ? e.substring(1) : e;
            if (id.indexOf(':') < 0) id = "minecraft:" + id;
            if (e.startsWith("#")) names.add("Any " + pretty(id.substring(id.indexOf(':') + 1)));
            else {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                Block b = rl == null ? null : BuiltInRegistries.BLOCK.get(rl);
                names.add(b == null ? pretty(id.substring(id.indexOf(':') + 1)) : b.getName().getString());
            }
        }
        return String.join(" or ", names);
    }

    static List<ItemStack> stacksFor(List<String> any) {
        List<ItemStack> out = new ArrayList<>();
        for (String e : any) {
            String id = e.startsWith("#") ? e.substring(1) : e;
            if (id.indexOf(':') < 0) id = "minecraft:" + id;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) continue;
            if (e.startsWith("#")) {
                Optional<HolderSet.Named<Block>> tag = BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, rl));
                if (tag.isPresent()) for (var hh : tag.get()) {
                    ItemStack st = new ItemStack(hh.value().asItem());
                    if (!st.isEmpty() && out.size() < 24) out.add(st);
                }
            } else {
                ItemStack st = new ItemStack(BuiltInRegistries.BLOCK.get(rl).asItem());
                if (!st.isEmpty()) out.add(st);
            }
        }
        return out;
    }

    static String pretty(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
