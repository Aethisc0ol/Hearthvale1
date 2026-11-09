package com.hearthvale.data;

import com.google.gson.JsonObject;
import com.hearthvale.data.BuildingRegistry;
import com.hearthvale.util.GameTime;
import com.hearthvale.util.Ids;
import com.hearthvale.util.Json;
import com.hearthvale.world.PlayerData;
import com.hearthvale.world.Relationship;
import com.hearthvale.world.WorldState;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Optional restrictions shared by greetings and events. All keys are optional and read flat from the
 * same JSON object:
 *   min_hearts, max_hearts, status ["friend","dating","engaged","married"],
 *   requires_event ["event_id" | "npc_ns:npc#event_id"], requires_flag [..], forbids_flag [..],
 *   time "morning|afternoon|evening|night|H:MM-H:MM", weather "rain|clear|storm",
 *   location "<building type>", season "spring|summer|fall|winter",
 *   weekday ["mon", "sat", ...], birthday true/false
 */
public final class Conditions {
    public static final Conditions NONE = new Conditions();

    public record Ctx(ServerLevel level, NpcDefinition def, PlayerData data, Relationship rel, String buildingType) {}

    private int minHearts = 0;
    private int maxHearts = Integer.MAX_VALUE;
    private Set<String> status = Set.of();
    private List<String> requiresEvent = List.of();
    private List<String> requiresFlag = List.of();
    private List<String> forbidsFlag = List.of();
    private List<String> requiresNpc = List.of();
    private int timeFrom = -1, timeTo = -1;
    private String timeKeyword;
    private String weather, location, season;
    private Set<Integer> weekdays = Set.of();
    private Boolean birthday;

    public static Conditions parse(JsonObject o) {
        Conditions c = new Conditions();
        c.minHearts = Json.integer(o, "min_hearts", 0);
        c.maxHearts = Json.integer(o, "max_hearts", Integer.MAX_VALUE);
        Set<String> st = new HashSet<>();
        for (String s : Json.strings(o, "status")) st.add(s.toLowerCase());
        c.status = st;
        c.requiresEvent = Json.strings(o, "requires_event");
        c.requiresFlag = Json.strings(o, "requires_flag");
        c.forbidsFlag = Json.strings(o, "forbids_flag");
        List<String> rn = new java.util.ArrayList<>();
        for (String s : Json.strings(o, "requires_npc")) rn.add(Ids.norm(s));
        c.requiresNpc = rn;
        String time = Json.str(o, "time", null);
        if (time != null) parseTime(c, time.toLowerCase().trim());
        c.weather = Json.str(o, "weather", null);
        String loc = Json.str(o, "location", null);
        c.location = loc == null ? null : Ids.norm(loc);
        c.season = Json.str(o, "season", null);
        Set<Integer> wd = new HashSet<>();
        for (String s : Json.strings(o, "weekday")) wd.add(GameTime.weekdayIndex(s));
        c.weekdays = wd;
        if (o.has("birthday")) c.birthday = Json.bool(o, "birthday", false);
        return c;
    }

    private static void parseTime(Conditions c, String t) {
        c.timeKeyword = switch (t) { case "morning", "afternoon", "evening", "night", "day" -> t; default -> null; };
        switch (t) {
            case "morning" -> { c.timeFrom = 0; c.timeTo = 6000; }
            case "afternoon" -> { c.timeFrom = 6000; c.timeTo = 12000; }
            case "evening" -> { c.timeFrom = 12000; c.timeTo = 16000; }
            case "night" -> { c.timeFrom = 16000; c.timeTo = 0; }
            case "day" -> { c.timeFrom = 0; c.timeTo = 12000; }
            default -> {
                String[] p = t.split("-");
                c.timeFrom = GameTime.parseClock(p[0]);
                c.timeTo = GameTime.parseClock(p[1]);
            }
        }
    }

    public boolean test(Ctx c) {
        int hearts = c.rel().hearts();
        if (hearts < minHearts || hearts > maxHearts) return false;
        if (!status.isEmpty() && !status.contains(c.rel().status.id())) return false;
        for (String token : requiresEvent) {
            String npcId = c.def().id;
            String ev = token;
            int h = token.indexOf('#');
            if (h >= 0) {
                npcId = Ids.norm(token.substring(0, h));
                ev = token.substring(h + 1);
            }
            Relationship r = c.data().relationships.get(npcId);
            if (r == null || !r.events.contains(ev)) return false;
        }
        for (String f : requiresFlag) if (!c.data().flags.contains(f)) return false;
        for (String f : forbidsFlag) if (c.data().flags.contains(f)) return false;
        for (String n : requiresNpc) if (WorldState.get(c.level()).villageOfNpc(n) == null) return false;
        ServerLevel level = c.level();
        if (timeFrom >= 0 && !GameTime.inRange(GameTime.tick(level), timeFrom, timeTo)) return false;
        if (weather != null) {
            boolean ok = switch (weather.toLowerCase()) {
                case "rain" -> level.isRaining();
                case "storm" -> level.isThundering();
                case "clear" -> !level.isRaining();
                default -> true;
            };
            if (!ok) return false;
        }
        if (location != null && !location.equals(c.buildingType())) return false;
        if (season != null && GameTime.season(level) != GameTime.seasonIndex(season)) return false;
        if (!weekdays.isEmpty() && !weekdays.contains(GameTime.weekday(level))) return false;
        if (birthday != null && c.def().isBirthday(level) != birthday) return false;
        return true;
    }

    /** Human-readable requirement lines for the Hearthvale book. */
    public List<String> describe(NpcDefinition self) {
        List<String> out = new java.util.ArrayList<>();
        boolean hasMin = minHearts > 0, hasMax = maxHearts != Integer.MAX_VALUE;
        if (hasMin && hasMax) out.add("Friendship: between " + minHearts + " and " + maxHearts + " hearts");
        else if (hasMin) out.add("Friendship: " + minHearts + " hearts or more");
        else if (hasMax) out.add("Friendship: " + maxHearts + " hearts or fewer");
        if (!status.isEmpty()) {
            List<String> s = new java.util.ArrayList<>();
            for (String x : new String[]{"friend", "dating", "engaged", "married"}) if (status.contains(x)) s.add(GameTime.capitalize(x));
            out.add("Relationship: " + String.join(" or ", s));
        }
        if (timeFrom >= 0) {
            String t = switch (timeKeyword == null ? "" : timeKeyword) {
                case "morning" -> "Morning";
                case "afternoon" -> "Afternoon";
                case "evening" -> "Evening";
                case "night" -> "Night";
                case "day" -> "Daytime";
                default -> "";
            };
            String range = GameTime.clockString(timeFrom) + " to " + GameTime.clockString(timeTo);
            out.add("Time of day: " + (t.isEmpty() ? range : t + " (" + range + ")"));
        }
        if (weather != null) {
            out.add("Weather: " + switch (weather.toLowerCase()) {
                case "rain" -> "Raining";
                case "storm" -> "Thunderstorm";
                case "clear" -> "Clear skies";
                default -> weather;
            });
        }
        if (location != null) {
            var bt = BuildingRegistry.get(location);
            out.add("Location: " + self.name + " must be inside a " + (bt != null ? bt.displayName : Ids.path(location)));
        }
        if (season != null) out.add("Season: " + GameTime.capitalize(season.toLowerCase()));
        if (!weekdays.isEmpty()) {
            List<String> days = new java.util.ArrayList<>();
            for (int i = 0; i < 7; i++) if (weekdays.contains(i)) days.add(GameTime.capitalize(GameTime.WEEKDAYS[i]));
            out.add("Day of the week: " + String.join(", ", days));
        }
        if (birthday != null) {
            out.add(birthday ? "Only on " + self.name + "'s birthday (" + self.birthdayLabel() + ")" : "Not on " + self.name + "'s birthday");
        }
        for (String token : requiresEvent) {
            NpcDefinition owner = self;
            String ev = token;
            int h = token.indexOf('#');
            if (h >= 0) {
                owner = com.hearthvale.data.NpcRegistry.get(Ids.norm(token.substring(0, h)));
                ev = token.substring(h + 1);
            }
            String name = NpcDefinition.prettify(ev);
            if (owner != null) for (NpcDefinition.NpcEvent e : owner.events) if (e.id().equals(ev)) name = e.name();
            out.add("Earlier event: " + name + (owner != null && owner != self ? " (" + owner.name + ")" : ""));
        }
        for (String n : requiresNpc) {
            NpcDefinition d = com.hearthvale.data.NpcRegistry.get(n);
            out.add("Requires " + (d == null ? Ids.path(n) : d.name) + " to live in the village");
        }
        if (!requiresFlag.isEmpty()) out.add("Depends on a choice you made in an earlier scene");
        if (!forbidsFlag.isEmpty()) out.add("Not available if you made a certain earlier choice");
        return out;
    }

    /** More restrictive conditions win when several greetings match. */
    public int specificity() {
        int s = 0;
        if (minHearts > 0 || maxHearts != Integer.MAX_VALUE) s++;
        if (!status.isEmpty()) s += 2;
        if (!requiresEvent.isEmpty() || !requiresFlag.isEmpty() || !forbidsFlag.isEmpty() || !requiresNpc.isEmpty()) s++;
        if (timeFrom >= 0) s++;
        if (weather != null) s += 2;
        if (location != null) s += 2;
        if (season != null) s++;
        if (!weekdays.isEmpty()) s++;
        if (birthday != null) s += 5;
        return s;
    }
}
