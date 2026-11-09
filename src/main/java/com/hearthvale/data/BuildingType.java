package com.hearthvale.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthvale.util.Ids;
import com.hearthvale.util.Json;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** A building type loaded from data/&lt;namespace&gt;/hearthvale/buildings/&lt;name&gt;.json */
public class BuildingType {

    /** "any" is a list of block ids or #block_tags; the room needs at least "count" matching blocks in total. */
    public record Req(List<String> any, int count) {
        public boolean matches(BlockState st) {
            for (String e : any) {
                if (e.startsWith("#")) {
                    ResourceLocation rl = ResourceLocation.tryParse(withNs(e.substring(1)));
                    if (rl != null && st.is(TagKey.<Block>create(Registries.BLOCK, rl))) return true;
                } else if (BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().equals(withNs(e))) {
                    return true;
                }
            }
            return false;
        }

        public int have(List<BlockState> furnishings) {
            int n = 0;
            for (BlockState s : furnishings) if (matches(s)) n++;
            return n;
        }

        public String label() {
            List<String> names = new ArrayList<>();
            for (String e : any) {
                String s = e.startsWith("#") ? e.substring(1) : e;
                s = Ids.path(withNs(s)).replace('_', ' ');
                names.add(s);
            }
            return String.join(" / ", names);
        }

        private static String withNs(String s) {
            return s.indexOf(':') >= 0 ? s : "minecraft:" + s;
        }
    }

    public final String id;
    public final String displayName;
    public final int priority;
    public final int minVolume;
    public final int maxVolume;
    public final boolean housing;
    public final List<Req> reqs;

    public BuildingType(String id, String displayName, int priority, int minVolume, int maxVolume, boolean housing, List<Req> reqs) {
        this.id = id;
        this.displayName = displayName;
        this.priority = priority;
        this.minVolume = minVolume;
        this.maxVolume = maxVolume;
        this.housing = housing;
        this.reqs = reqs;
    }

    public static BuildingType parse(String id, JsonObject o) {
        List<Req> reqs = new ArrayList<>();
        for (JsonElement e : Json.list(o, "requires")) {
            JsonObject r = e.getAsJsonObject();
            reqs.add(new Req(Json.strings(r, "any"), Math.max(1, Json.integer(r, "count", 1))));
        }
        return new BuildingType(Ids.norm(id), Json.str(o, "display_name", Ids.path(id)),
                Json.integer(o, "priority", 10), Json.integer(o, "min_volume", 8),
                Json.integer(o, "max_volume", 100000), Json.bool(o, "housing", false), reqs);
    }

    /** Human readable list of what the room still lacks (empty = matches). */
    public List<String> missing(List<BlockState> furnishings) {
        List<String> out = new ArrayList<>();
        for (Req r : reqs) {
            int have = r.have(furnishings);
            if (have < r.count()) out.add((r.count() - have) + " more " + r.label());
        }
        return out;
    }

    public int weight() {
        int w = 0;
        for (Req r : reqs) w += r.count();
        return w;
    }
}
