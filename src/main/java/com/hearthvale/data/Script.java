package com.hearthvale.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthvale.util.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * A linear conversation. Each line may offer choices; a choice can have a nested "reply" script,
 * after which the main script continues with the next line.
 *
 * JSON forms accepted anywhere a script is expected:
 *   "just a string"
 *   ["line one", {"say": "line two", "choices": [...]}, ...]
 *   {"say": "...", ...}
 */
public record Script(List<Line> lines) {

    public record Line(String speaker, String text, List<Choice> choices, List<ScriptEffect> effects) {
        public static Line say(String text) {
            return new Line(null, text, List.of(), List.of());
        }
    }

    public record Choice(String text, Script reply, List<ScriptEffect> effects) {}

    public static Script of(String... texts) {
        List<Line> l = new ArrayList<>();
        for (String t : texts) l.add(Line.say(t));
        return new Script(l);
    }

    public static Script parse(JsonElement e) {
        List<Line> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return new Script(out);
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) out.add(parseOne(x));
        } else {
            out.add(parseOne(e));
        }
        return new Script(out);
    }

    private static Line parseOne(JsonElement e) {
        if (e.isJsonPrimitive()) return Line.say(e.getAsString());
        return parseLine(e.getAsJsonObject());
    }

    public static Line parseLine(JsonObject o) {
        List<Choice> choices = new ArrayList<>();
        for (JsonElement c : Json.list(o, "choices")) {
            JsonObject co = c.getAsJsonObject();
            Script reply = co.has("reply") ? parse(co.get("reply")) : new Script(List.of());
            choices.add(new Choice(Json.str(co, "text", "..."), reply, Effects.parseShorthand(co)));
        }
        return new Line(Json.str(o, "speaker", null), Json.str(o, "say", ""), choices, Effects.parseShorthand(o));
    }
}
