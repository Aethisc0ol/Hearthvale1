package com.hearthvale.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.hearthvale.Hearthvale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads every data/&lt;ns&gt;/hearthvale/buildings/*.json and classifies scanned rooms. */
public class BuildingRegistry extends SimpleJsonResourceReloadListener {
    private static volatile Map<String, BuildingType> TYPES = Map.of();

    public record Match(BuildingType type, BuildingType closest, List<String> missing) {}

    public BuildingRegistry() {
        super(new Gson(), "hearthvale/buildings");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, BuildingType> out = new LinkedHashMap<>();
        objects.forEach((rl, el) -> {
            try {
                BuildingType t = BuildingType.parse(rl.toString(), el.getAsJsonObject());
                out.put(t.id, t);
            } catch (Exception ex) {
                Hearthvale.LOGGER.error("Failed to load building type {}: {}", rl, ex.toString());
            }
        });
        TYPES = out;
        Hearthvale.LOGGER.info("Hearthvale: loaded {} building types", out.size());
    }

    public static BuildingType get(String id) { return TYPES.get(id); }
    public static Collection<BuildingType> all() { return TYPES.values(); }

    public static Match classify(int volume, List<BlockState> furnishings) {
        BuildingType best = null;
        BuildingType closest = null;
        List<String> closestMissing = List.of();
        for (BuildingType t : TYPES.values()) {
            if (volume > t.maxVolume) continue;
            List<String> missing = t.missing(furnishings);
            if (volume < t.minVolume) missing = List.of("a bigger room (" + t.minVolume + "+ open blocks, yours has " + volume + ")");
            if (missing.isEmpty()) {
                if (best == null || t.priority > best.priority || (t.priority == best.priority && t.weight() > best.weight())) best = t;
            } else if (closest == null || missing.size() < closestMissing.size()
                    || (missing.size() == closestMissing.size() && t.priority > closest.priority)) {
                closest = t;
                closestMissing = missing;
            }
        }
        return new Match(best, closest, closestMissing);
    }
}
