package com.hearthvale.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** One player's relationship with one NPC (Stardew-style: 250 points per heart). */
public class Relationship {
    public enum Status {
        FRIEND, DATING, ENGAGED, MARRIED;

        public String id() { return name().toLowerCase(Locale.ROOT); }
        public String label() { return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT); }
    }

    public static final int POINTS_PER_HEART = 250;

    public int points;
    public Status status = Status.FRIEND;
    public long weddingDay = -1;
    public long talkedDay = -1, giftedDay = -1, affectionDay = -1, giftWeek = -1;
    public int giftsThisWeek;
    public final Set<String> events = new HashSet<>();

    public int hearts() { return points / POINTS_PER_HEART; }

    /** Romanceable NPCs stop at 8 hearts until you date them; dating allows 10; marriage allows 14. */
    public int maxPoints(boolean romanceable) {
        return switch (status) {
            case MARRIED -> 14 * POINTS_PER_HEART;
            case DATING, ENGAGED -> 10 * POINTS_PER_HEART;
            default -> (romanceable ? 8 : 10) * POINTS_PER_HEART;
        };
    }

    /** Returns the actual change after clamping. */
    public int add(int delta, boolean romanceable) {
        int old = points;
        points = Math.max(0, Math.min(maxPoints(romanceable), points + delta));
        return points - old;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("points", points);
        t.putString("status", status.name());
        t.putLong("weddingDay", weddingDay);
        t.putLong("talkedDay", talkedDay);
        t.putLong("giftedDay", giftedDay);
        t.putLong("affectionDay", affectionDay);
        t.putLong("giftWeek", giftWeek);
        t.putInt("giftsThisWeek", giftsThisWeek);
        ListTag ev = new ListTag();
        for (String e : events) ev.add(StringTag.valueOf(e));
        t.put("events", ev);
        return t;
    }

    public static Relationship load(CompoundTag t) {
        Relationship r = new Relationship();
        r.points = t.getInt("points");
        try { r.status = Status.valueOf(t.getString("status")); } catch (IllegalArgumentException ignored) {}
        r.weddingDay = t.getLong("weddingDay");
        r.talkedDay = t.getLong("talkedDay");
        r.giftedDay = t.getLong("giftedDay");
        r.affectionDay = t.getLong("affectionDay");
        r.giftWeek = t.getLong("giftWeek");
        r.giftsThisWeek = t.getInt("giftsThisWeek");
        ListTag ev = t.getList("events", Tag.TAG_STRING);
        for (int i = 0; i < ev.size(); i++) r.events.add(ev.getString(i));
        return r;
    }
}
