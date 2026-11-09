package com.hearthvale.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Village {
    public final UUID id;
    public String name;
    public final ResourceKey<Level> dim;
    public final BlockPos hearth;
    public UUID owner;
    public long lastArrival;
    public final Map<UUID, Building> buildings = new LinkedHashMap<>();
    /** Keyed by NPC id. */
    public final Map<String, Resident> residents = new LinkedHashMap<>();

    public Village(UUID id, String name, ResourceKey<Level> dim, BlockPos hearth, UUID owner) {
        this.id = id;
        this.name = name;
        this.dim = dim;
        this.hearth = hearth;
        this.owner = owner;
    }

    public Building buildingAt(BlockPos p) {
        for (Building b : buildings.values()) if (b.contains(p)) return b;
        return null;
    }

    public List<Building> ofType(String type) {
        List<Building> out = new ArrayList<>();
        for (Building b : buildings.values()) if (b.type.equals(type)) out.add(b);
        return out;
    }

    public boolean hasType(String type) {
        for (Building b : buildings.values()) if (b.type.equals(type)) return true;
        return false;
    }

    public int occupants(Building b) {
        int n = 0;
        for (Resident r : residents.values()) if (b.id.equals(r.home)) n++;
        return n;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("dim", dim.location().toString());
        t.putLong("hearth", hearth.asLong());
        if (owner != null) t.putUUID("owner", owner);
        t.putLong("lastArrival", lastArrival);
        ListTag bl = new ListTag();
        for (Building b : buildings.values()) bl.add(b.save());
        t.put("buildings", bl);
        ListTag rl = new ListTag();
        for (Resident r : residents.values()) rl.add(r.save());
        t.put("residents", rl);
        return t;
    }

    public static Village load(CompoundTag t) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim")));
        Village v = new Village(t.getUUID("id"), t.getString("name"), dim, BlockPos.of(t.getLong("hearth")),
                t.hasUUID("owner") ? t.getUUID("owner") : null);
        v.lastArrival = t.getLong("lastArrival");
        ListTag bl = t.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < bl.size(); i++) {
            Building b = Building.load(bl.getCompound(i));
            v.buildings.put(b.id, b);
        }
        ListTag rl = t.getList("residents", Tag.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            Resident r = Resident.load(rl.getCompound(i));
            v.residents.put(r.npcId, r);
        }
        return v;
    }
}
