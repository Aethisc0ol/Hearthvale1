package com.hearthvale.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.hearthvale.Hearthvale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads every data/&lt;ns&gt;/hearthvale/npcs/*.json (datapacks and mod jars alike) on /reload. */
public class NpcRegistry extends SimpleJsonResourceReloadListener {
    private static volatile Map<String, NpcDefinition> DEFS = Map.of();

    public NpcRegistry() {
        super(new Gson(), "hearthvale/npcs");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, NpcDefinition> out = new LinkedHashMap<>();
        objects.forEach((rl, el) -> {
            try {
                NpcDefinition d = NpcDefinition.parse(rl.toString(), el.getAsJsonObject());
                out.put(d.id, d);
            } catch (Exception ex) {
                Hearthvale.LOGGER.error("Failed to load NPC {}: {}", rl, ex.toString());
            }
        });
        DEFS = out;
        Hearthvale.LOGGER.info("Hearthvale: loaded {} NPC definitions", out.size());
    }

    public static NpcDefinition get(String id) { return DEFS.get(id); }
    public static Collection<NpcDefinition> all() { return DEFS.values(); }
}
