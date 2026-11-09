package com.hearthvale.data;

import com.google.gson.JsonObject;
import com.hearthvale.util.GameTime;
import com.hearthvale.util.Json;

import java.util.HashSet;
import java.util.Set;

/**
 * One slot of an NPC's day. The first entry that matches wins, so put special cases (rain, weekends) first.
 * "at" is one of: home, work, hearth, wander, building:&lt;type&gt;
 */
public record ScheduleEntry(int from, int to, String at, String weather, Set<Integer> days) {

    public static ScheduleEntry parse(JsonObject o) {
        int from = GameTime.parseClock(Json.str(o, "from", "6:00"));
        int to = GameTime.parseClock(Json.str(o, "to", "6:00"));
        Set<Integer> days = new HashSet<>();
        for (String d : Json.strings(o, "days")) days.add(GameTime.weekdayIndex(d));
        return new ScheduleEntry(from, to, Json.str(o, "at", "hearth").toLowerCase(), Json.str(o, "weather", null), days);
    }

    public boolean matches(int tick, boolean raining, int weekday) {
        if (!GameTime.inRange(tick, from, to)) return false;
        if (!days.isEmpty() && !days.contains(weekday)) return false;
        if (weather != null) {
            if (weather.equalsIgnoreCase("rain") && !raining) return false;
            if (weather.equalsIgnoreCase("clear") && raining) return false;
        }
        return true;
    }
}
