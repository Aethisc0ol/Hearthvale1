package com.hearthvale.client.book;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side copy of what the server sent for the book. */
final class BookData {
    record Req(List<String> any, int count) {}
    record Building(String id, String name, int minVolume, boolean housing, List<Req> reqs) {}
    record Event(String id, String name, boolean approach, boolean seen, List<String> reqs) {}
    record Npc(String id, String name, boolean slim, boolean romance, String birthday, String summary, String backstory,
               String home, String work, List<String> requires, int points, String status, boolean met, List<Event> events) {
        int maxHearts() { return Pix.maxHearts(status, romance); }
        int hearts() { return Math.min(maxHearts(), points / 250); }
    }
    record Settings(boolean op, int arrivalMinutes, boolean autoEvents, int maxVolume) {}

    final List<Npc> npcs = new ArrayList<>();
    final Map<String, Building> buildings = new LinkedHashMap<>();
    Settings settings = new Settings(false, 5, true, 3000);

    Npc npc(String id) {
        for (Npc n : npcs) if (n.id().equals(id)) return n;
        return null;
    }

    int metCount() {
        int c = 0;
        for (Npc n : npcs) if (n.met()) c++;
        return c;
    }

    static BookData parse(CompoundTag t) {
        BookData d = new BookData();
        ListTag npcs = t.getList("npcs", Tag.TAG_COMPOUND);
        for (int i = 0; i < npcs.size(); i++) {
            CompoundTag n = npcs.getCompound(i);
            List<Event> events = new ArrayList<>();
            ListTag evs = n.getList("events", Tag.TAG_COMPOUND);
            for (int j = 0; j < evs.size(); j++) {
                CompoundTag e = evs.getCompound(j);
                events.add(new Event(e.getString("id"), e.getString("name"), e.getBoolean("approach"), e.getBoolean("seen"), strings(e, "reqs")));
            }
            d.npcs.add(new Npc(n.getString("id"), n.getString("name"), n.getBoolean("slim"), n.getBoolean("romance"),
                    n.getString("birthday"), n.getString("summary"), n.getString("backstory"), n.getString("home"), n.getString("work"),
                    strings(n, "requires"), n.getInt("points"), n.getString("status"), n.getBoolean("met"), events));
        }
        ListTag bs = t.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < bs.size(); i++) {
            CompoundTag b = bs.getCompound(i);
            List<Req> reqs = new ArrayList<>();
            ListTag rs = b.getList("reqs", Tag.TAG_COMPOUND);
            for (int j = 0; j < rs.size(); j++) reqs.add(new Req(strings(rs.getCompound(j), "any"), rs.getCompound(j).getInt("count")));
            d.buildings.put(b.getString("id"), new Building(b.getString("id"), b.getString("name"), b.getInt("minVolume"), b.getBoolean("housing"), reqs));
        }
        if (t.contains("settings")) {
            CompoundTag s = t.getCompound("settings");
            d.settings = new Settings(s.getBoolean("op"), s.getInt("arrivalMinutes"), s.getBoolean("autoEvents"), s.getInt("maxVolume"));
        }
        return d;
    }

    private static List<String> strings(CompoundTag t, String key) {
        List<String> out = new ArrayList<>();
        ListTag l = t.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }
}
