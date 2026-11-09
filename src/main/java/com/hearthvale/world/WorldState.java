package com.hearthvale.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** All persistent Hearthvale data, stored once per world in the overworld's data folder. */
public class WorldState extends SavedData {
    private static final String NAME = "hearthvale_state";

    private final Map<UUID, Village> villages = new LinkedHashMap<>();
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public static WorldState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(WorldState::new, WorldState::load, null), NAME);
    }

    public static WorldState get(ServerLevel level) {
        return get(level.getServer());
    }

    public Collection<Village> villages() { return villages.values(); }
    public Village village(UUID id) { return id == null ? null : villages.get(id); }

    public void addVillage(Village v) {
        villages.put(v.id, v);
        setDirty();
    }

    public void removeVillage(UUID id) {
        villages.remove(id);
        setDirty();
    }

    public PlayerData player(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    /** The village an NPC currently belongs to (each NPC exists once per world). */
    public Village villageOfNpc(String npcId) {
        for (Village v : villages.values()) if (v.residents.containsKey(npcId)) return v;
        return null;
    }

    /** UUID of the player married to this NPC, or null. */
    public UUID spouseOf(String npcId) {
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            Relationship r = e.getValue().relationships.get(npcId);
            if (r != null && r.status == Relationship.Status.MARRIED) return e.getKey();
        }
        return null;
    }

    public Village nearest(ResourceKey<Level> dim, BlockPos pos, double range) {
        Village best = null;
        double bestD = range * range;
        for (Village v : villages.values()) {
            if (!v.dim.equals(dim)) continue;
            double d = v.hearth.distSqr(pos);
            if (d <= bestD) {
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag vl = new ListTag();
        for (Village v : villages.values()) vl.add(v.save());
        tag.put("villages", vl);
        ListTag pl = new ListTag();
        players.forEach((id, pd) -> {
            CompoundTag t = pd.save();
            t.putUUID("uuid", id);
            pl.add(t);
        });
        tag.put("players", pl);
        return tag;
    }

    public static WorldState load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldState s = new WorldState();
        ListTag vl = tag.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < vl.size(); i++) {
            Village v = Village.load(vl.getCompound(i));
            s.villages.put(v.id, v);
        }
        ListTag pl = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            CompoundTag t = pl.getCompound(i);
            s.players.put(t.getUUID("uuid"), PlayerData.load(t));
        }
        return s;
    }
}
