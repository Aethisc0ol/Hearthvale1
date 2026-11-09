package com.hearthvale.cutscene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthvale.data.Conditions;
import com.hearthvale.data.Effects;
import com.hearthvale.data.Script;
import com.hearthvale.data.ScriptEffect;
import com.hearthvale.util.Json;

import java.util.*;

/**
 * A parsed cutscene: a tree of steps run by {@link CutsceneRunner}. See docs/CUTSCENE_GUIDE.md.
 * Parsing validates step names/fields up front so addon mistakes show up in the log at load time.
 */
public final class Cutscene {
    public static final Set<String> TYPES = Set.of("talk", "wait", "sync", "fade", "letterbox", "title", "camera",
            "move", "warp", "face", "emote", "sound", "animate", "hold", "spawn", "despawn", "effect", "if", "end");

    public static final class Step {
        public final String type;
        public final JsonObject json;
        public final boolean wait;
        public Script script;                       // talk
        public List<List<Step>> branches;           // talk: one entry per choice of the final line (nullable)
        public Conditions cond;                     // if
        public List<Step> then = List.of();         // if
        public List<Step> otherwise = List.of();    // if
        public List<ScriptEffect> effects = List.of(); // effect

        Step(String type, JsonObject json, boolean wait) {
            this.type = type;
            this.json = json;
            this.wait = wait;
        }
    }

    public final boolean skippable;
    public final boolean returnPlayer;
    public final boolean restoreNpcs;
    public final JsonElement origin;
    public final JsonElement playerStart;
    public final Map<String, JsonElement> marks;
    public final Map<String, String> actors;
    public final List<Step> steps;

    private Cutscene(boolean skippable, boolean returnPlayer, boolean restoreNpcs, JsonElement origin, JsonElement playerStart,
                     Map<String, JsonElement> marks, Map<String, String> actors, List<Step> steps) {
        this.skippable = skippable;
        this.returnPlayer = returnPlayer;
        this.restoreNpcs = restoreNpcs;
        this.origin = origin;
        this.playerStart = playerStart;
        this.marks = marks;
        this.actors = actors;
        this.steps = steps;
    }

    public static Cutscene parse(JsonObject o) {
        List<Step> steps = parseSteps(o.has("steps") ? o.getAsJsonArray("steps") : new JsonArray());
        boolean skippableDefault = !containsChoices(steps);
        Map<String, JsonElement> marks = new HashMap<>();
        if (o.has("marks")) o.getAsJsonObject("marks").entrySet().forEach(e -> marks.put(e.getKey(), e.getValue()));
        Map<String, String> actors = new LinkedHashMap<>();
        if (o.has("actors")) o.getAsJsonObject("actors").entrySet().forEach(e -> actors.put(e.getKey(), com.hearthvale.util.Ids.norm(e.getValue().getAsString())));
        return new Cutscene(Json.bool(o, "skippable", skippableDefault), Json.bool(o, "return_player", true),
                Json.bool(o, "restore_npcs", true), o.get("origin"), o.get("player_start"), marks, actors, steps);
    }

    private static boolean containsChoices(List<Step> steps) {
        for (Step s : steps) {
            if (s.script != null) for (Script.Line l : s.script.lines()) if (!l.choices().isEmpty()) return true;
            if (containsChoices(s.then) || containsChoices(s.otherwise)) return true;
            if (s.branches != null) for (List<Step> b : s.branches) if (containsChoices(b)) return true;
        }
        return false;
    }

    private static List<Step> parseSteps(JsonArray arr) {
        List<Step> out = new ArrayList<>();
        Step talk = null;
        List<Script.Line> talkLines = null;

        for (JsonElement e : arr) {
            JsonObject o;
            if (e.isJsonPrimitive()) {
                o = new JsonObject();
                o.addProperty("say", e.getAsString());
            } else {
                o = e.getAsJsonObject();
            }
            String type = Json.str(o, "do", o.has("say") ? "talk" : null);
            if (type == null) throw new IllegalArgumentException("cutscene step needs a \"do\" (or a \"say\"): " + o);
            if (type.equals("say")) type = "talk";
            if (!TYPES.contains(type)) throw new IllegalArgumentException("unknown cutscene step '" + type + "'");

            if (type.equals("talk")) {
                if (talk == null) {
                    talkLines = new ArrayList<>();
                    talk = new Step("talk", o, true);
                    out.add(talk);
                }
                Script.Line line = Script.parseLine(o);
                List<JsonElement> choiceJson = Json.list(o, "choices");
                boolean branching = false;
                for (JsonElement c : choiceJson) if (c.getAsJsonObject().has("steps")) branching = true;
                if (branching) {
                    List<List<Step>> branches = new ArrayList<>();
                    List<Script.Choice> newChoices = new ArrayList<>();
                    for (int i = 0; i < choiceJson.size(); i++) {
                        JsonObject co = choiceJson.get(i).getAsJsonObject();
                        branches.add(co.has("steps") ? parseSteps(co.getAsJsonArray("steps")) : List.of());
                        Script.Choice c = line.choices().get(i);
                        List<ScriptEffect> eff = new ArrayList<>(c.effects());
                        final int idx = i;
                        eff.add(ctx -> CutsceneManager.chooseBranch(ctx.player().getUUID(), idx));
                        newChoices.add(new Script.Choice(c.text(), c.reply(), eff));
                    }
                    line = new Script.Line(line.speaker(), line.text(), newChoices, line.effects());
                    talkLines.add(line);
                    talk.script = new Script(talkLines);
                    talk.branches = branches;
                    talk = null; // a branching line closes the group
                } else {
                    talkLines.add(line);
                    talk.script = new Script(talkLines);
                }
                continue;
            }
            talk = null;

            boolean defWait = switch (type) {
                case "wait", "sync", "fade", "title", "camera", "move" -> true;
                default -> false;
            };
            Step s = new Step(type, o, type.equals("sync") || type.equals("wait") || Json.bool(o, "wait", defWait));
            switch (type) {
                case "if" -> {
                    s.cond = Conditions.parse(o.has("if") ? o.getAsJsonObject("if") : new JsonObject());
                    s.then = o.has("then") ? parseSteps(o.getAsJsonArray("then")) : List.of();
                    s.otherwise = o.has("else") ? parseSteps(o.getAsJsonArray("else")) : List.of();
                }
                case "effect" -> s.effects = Effects.parseShorthand(o);
                case "move", "warp" -> { if (!o.has("to")) throw new IllegalArgumentException("'" + type + "' needs a \"to\""); }
                case "spawn" -> { if (!o.has("actor") || !o.has("at")) throw new IllegalArgumentException("'spawn' needs \"actor\" and \"at\""); }
                case "despawn" -> { if (!o.has("actor")) throw new IllegalArgumentException("'despawn' needs \"actor\""); }
                default -> { }
            }
            out.add(s);
        }
        return out;
    }
}
