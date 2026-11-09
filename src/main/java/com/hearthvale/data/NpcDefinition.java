package com.hearthvale.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthvale.cutscene.Cutscene;
import com.hearthvale.util.GameTime;
import com.hearthvale.util.Ids;
import com.hearthvale.util.Json;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** A fully parsed NPC loaded from data/&lt;namespace&gt;/hearthvale/npcs/&lt;name&gt;.json */
public class NpcDefinition {
    public record Greeting(Script.Line line, Conditions cond) {}
    /** An event is either a plain dialogue `script` or a full `cutscene` (camera, movement, extras...). */
    public record NpcEvent(String id, String name, Conditions cond, boolean approach, Script script, Cutscene cutscene) {}

    public static final String[] GIFT_CATEGORIES = {"loved", "liked", "neutral", "disliked", "hated"};

    public String id;
    public String name;
    public boolean slim;
    public boolean romanceable;
    public int birthSeason = -1;
    public int birthDay = -1;
    /** Building type this NPC lives in (null = homeless). */
    public String homeType = Ids.norm("home");
    /** Building type this NPC works in (nullable). */
    public String workType;
    /** Shown in the Hearthvale book. Keep spoilers (things revealed by events) out of these. */
    public String summary = "", backstory = "";
    /** Building types that must exist in a village before this NPC will arrive. */
    public List<String> requires = new ArrayList<>();
    public List<ScheduleEntry> schedule = new ArrayList<>();
    public List<ScheduleEntry> spouseSchedule = new ArrayList<>();
    public Map<String, List<String>> gifts = new HashMap<>();
    public Map<String, List<String>> reactions = new HashMap<>();
    public Script arrival, bouquetAccept, bouquetReject, pendantAccept, pendantReject, wedding;
    public List<Greeting> greetings = new ArrayList<>();
    public List<NpcEvent> events = new ArrayList<>();

    public static NpcDefinition parse(String id, JsonObject o) {
        NpcDefinition d = new NpcDefinition();
        d.id = Ids.norm(id);
        d.name = Json.str(o, "name", Ids.path(d.id));
        d.slim = Json.bool(o, "slim", false);
        d.romanceable = Json.bool(o, "romanceable", false);
        if (o.has("birthday") && o.get("birthday").isJsonObject()) {
            JsonObject b = o.getAsJsonObject("birthday");
            d.birthSeason = GameTime.seasonIndex(Json.str(b, "season", "spring"));
            d.birthDay = Json.integer(b, "day", 1);
        }
        String home = Json.str(o, "home", "home");
        d.homeType = home.equalsIgnoreCase("none") ? null : Ids.norm(home);
        String work = Json.str(o, "workplace", null);
        d.workType = work == null ? null : Ids.norm(work);
        for (String r : Json.strings(o, "requires")) d.requires.add(Ids.norm(r));

        if (o.has("profile") && o.get("profile").isJsonObject()) {
            JsonObject p = o.getAsJsonObject("profile");
            d.summary = Json.str(p, "summary", "");
            d.backstory = Json.str(p, "backstory", "");
        }
        d.schedule = parseSchedule(o, "schedule", defaultSchedule(d.workType != null));
        d.spouseSchedule = parseSchedule(o, "spouse_schedule", defaultSpouseSchedule());

        readStringMap(o, "gifts", d.gifts);
        readStringMap(o, "gift_reactions", d.reactions);

        d.arrival = optScript(o, "arrival");
        d.bouquetAccept = optScript(o, "bouquet_accept");
        d.bouquetReject = optScript(o, "bouquet_reject");
        d.pendantAccept = optScript(o, "pendant_accept");
        d.pendantReject = optScript(o, "pendant_reject");
        d.wedding = optScript(o, "wedding");

        for (JsonElement e : Json.list(o, "greetings")) {
            JsonObject g = e.getAsJsonObject();
            d.greetings.add(new Greeting(Script.parseLine(g), Conditions.parse(g)));
        }
        for (JsonElement e : Json.list(o, "events")) {
            JsonObject ev = e.getAsJsonObject();
            String evId = Json.str(ev, "id", null);
            if (evId == null) throw new IllegalArgumentException("event without an id in " + id);
            boolean approach = !"talk".equalsIgnoreCase(Json.str(ev, "trigger", "approach"));
            Cutscene cutscene = ev.has("cutscene") ? Cutscene.parse(ev.getAsJsonObject("cutscene")) : null;
            if (cutscene == null && !ev.has("script")) throw new IllegalArgumentException("event '" + evId + "' needs a \"script\" or a \"cutscene\"");
            Script script = ev.has("script") ? Script.parse(ev.get("script")) : new Script(List.of());
            d.events.add(new NpcEvent(evId, Json.str(ev, "name", prettify(evId)), Conditions.parse(ev), approach, script, cutscene));
        }
        return d;
    }

    public static String prettify(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.replace('-', '_').split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static Script optScript(JsonObject o, String key) {
        return o.has(key) ? Script.parse(o.get(key)) : null;
    }

    private static void readStringMap(JsonObject o, String key, Map<String, List<String>> out) {
        if (!o.has(key) || !o.get(key).isJsonObject()) return;
        JsonObject m = o.getAsJsonObject(key);
        for (String cat : GIFT_CATEGORIES) out.put(cat, Json.strings(m, cat));
    }

    private static List<ScheduleEntry> parseSchedule(JsonObject o, String key, List<ScheduleEntry> fallback) {
        List<ScheduleEntry> out = new ArrayList<>();
        for (JsonElement e : Json.list(o, key)) out.add(ScheduleEntry.parse(e.getAsJsonObject()));
        return out.isEmpty() ? fallback : out;
    }

    private static List<ScheduleEntry> defaultSchedule(boolean hasWork) {
        return List.of(
                new ScheduleEntry(0, 6000, hasWork ? "work" : "wander", null, Set.of()),
                new ScheduleEntry(6000, 12000, hasWork ? "work" : "wander", null, Set.of()),
                new ScheduleEntry(12000, 16000, "hearth", null, Set.of()),
                new ScheduleEntry(16000, 0, "home", null, Set.of()));
    }

    private static List<ScheduleEntry> defaultSpouseSchedule() {
        return List.of(
                new ScheduleEntry(0, 4000, "home", null, Set.of()),
                new ScheduleEntry(4000, 10000, "hearth", null, Set.of()),
                new ScheduleEntry(10000, 0, "home", null, Set.of()));
    }

    /** loved / liked / neutral / disliked / hated. Exact item ids beat tags. */
    public String giftCategory(ItemStack stack) {
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String[] order = {"loved", "liked", "disliked", "hated"};
        for (String cat : order) {
            for (String g : gifts.getOrDefault(cat, List.of())) if (!g.startsWith("#") && g.equals(key)) return cat;
        }
        for (String cat : order) {
            for (String g : gifts.getOrDefault(cat, List.of())) {
                if (!g.startsWith("#")) continue;
                ResourceLocation rl = ResourceLocation.tryParse(g.substring(1));
                if (rl != null && stack.is(TagKey.create(Registries.ITEM, rl))) return cat;
            }
        }
        return "neutral";
    }

    public boolean isBirthday(ServerLevel level) {
        return birthSeason >= 0 && GameTime.season(level) == birthSeason && GameTime.dayOfSeason(level) == birthDay;
    }

    public String birthdayLabel() {
        return birthSeason < 0 ? "?" : GameTime.capitalize(GameTime.SEASONS[birthSeason]) + " " + birthDay;
    }
}
