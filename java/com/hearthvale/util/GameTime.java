package com.hearthvale.util;

import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/**
 * Stardew-style calendar built on Minecraft's day counter.
 * Day 0 is a Monday. A week is 7 days, a season is 28 days, a year is 4 seasons.
 * Clock times in JSON ("6:30") map onto ticks: 6:00 = tick 0, 12:00 = tick 6000, 18:00 = tick 12000.
 */
public final class GameTime {
    public static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    public static final String[] WEEKDAYS = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};

    private GameTime() {}

    private static long dayTime(ServerLevel l) {
        return l.getServer().overworld().getDayTime();
    }

    public static long day(ServerLevel l) { return dayTime(l) / 24000L; }
    public static int tick(ServerLevel l) { return (int) (dayTime(l) % 24000L); }
    public static int weekday(ServerLevel l) { return (int) (day(l) % 7L); }
    public static int season(ServerLevel l) { return (int) ((day(l) / 28L) % 4L); }
    public static int dayOfSeason(ServerLevel l) { return (int) (day(l) % 28L) + 1; }

    public static int seasonIndex(String s) {
        s = s.toLowerCase(Locale.ROOT);
        if (s.equals("autumn")) s = "fall";
        for (int i = 0; i < SEASONS.length; i++) if (SEASONS[i].equals(s)) return i;
        return 0;
    }

    public static int weekdayIndex(String s) {
        s = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < WEEKDAYS.length; i++) if (WEEKDAYS[i].startsWith(s.length() >= 3 ? s.substring(0, 3) : s)) return i;
        return 0;
    }

    /** "6:30" -> ticks since 6:00. */
    public static int parseClock(String s) {
        String[] p = s.trim().split(":");
        int h = Integer.parseInt(p[0].trim());
        int m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
        return Math.floorMod(h * 1000 + m * 1000 / 60 - 6000, 24000);
    }

    /** Range check that supports wrapping past midnight; from == to means "all day". */
    public static boolean inRange(int t, int from, int to) {
        if (from == to) return true;
        if (from < to) return t >= from && t < to;
        return t >= from || t < to;
    }

    /** tick (0 = 6:00 AM) -> "7:30 AM". */
    public static String clockString(int tick) {
        int mins = ((int) (tick * 60L / 1000L) + 6 * 60) % 1440;
        int h = mins / 60, m = mins % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
    }

    public static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
