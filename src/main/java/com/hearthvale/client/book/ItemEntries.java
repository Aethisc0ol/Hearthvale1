package com.hearthvale.client.book;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hearthvale.Hearthvale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Text for the Items & Blocks tab, loaded from assets/hearthvale/book/items.json (so resource packs can edit it). */
final class ItemEntries {
    record Entry(ResourceLocation item, String kind, ResourceLocation recipe, List<String> description, List<String> usage) {}

    final List<String> intro = new ArrayList<>();
    final List<Entry> entries = new ArrayList<>();

    static ItemEntries load() {
        ItemEntries out = new ItemEntries();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "book/items.json"));
            if (res.isPresent()) {
                try (Reader r = res.get().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (JsonElement e : o.getAsJsonArray("intro")) out.intro.add(e.getAsString());
                    for (JsonElement e : o.getAsJsonArray("items")) {
                        JsonObject i = e.getAsJsonObject();
                        out.entries.add(new Entry(ResourceLocation.parse(i.get("item").getAsString()), i.get("kind").getAsString(),
                                i.has("recipe") ? ResourceLocation.parse(i.get("recipe").getAsString()) : null,
                                strings(i, "description"), strings(i, "usage")));
                    }
                }
            }
        } catch (Exception ex) {
            Hearthvale.LOGGER.error("Could not load book/items.json", ex);
        }
        return out;
    }

    private static List<String> strings(JsonObject o, String k) {
        List<String> l = new ArrayList<>();
        if (o.has(k)) for (JsonElement e : o.getAsJsonArray(k)) l.add(e.getAsString());
        return l;
    }
}
