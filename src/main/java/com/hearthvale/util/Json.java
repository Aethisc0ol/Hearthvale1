package com.hearthvale.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Tolerant JSON accessors so addon authors get defaults instead of crashes. */
public final class Json {
    private Json() {}

    public static String str(JsonObject o, String k, String d) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : d;
    }

    public static int integer(JsonObject o, String k, int d) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : d;
    }

    public static double dbl(JsonObject o, String k, double d) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : d;
    }

    public static boolean bool(JsonObject o, String k, boolean d) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : d;
    }

    /** Returns the value as a list: arrays are expanded, single values become a 1-element list. */
    public static List<JsonElement> list(JsonObject o, String k) {
        JsonElement e = o.get(k);
        List<JsonElement> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        if (e.isJsonArray()) e.getAsJsonArray().forEach(out::add);
        else out.add(e);
        return out;
    }

    public static List<String> strings(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : list(o, k)) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }
}
