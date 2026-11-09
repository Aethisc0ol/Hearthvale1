package com.hearthvale.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A detected room that has been classified as a building type. */
public class Building {
    public final UUID id;
    public String type;
    public BlockPos min, max, seed;
    public int capacity;
    /** Standing positions inside the building that NPCs walk to. */
    public List<BlockPos> spots = new ArrayList<>();

    public Building(UUID id, String type, BlockPos min, BlockPos max, BlockPos seed, int capacity, List<BlockPos> spots) {
        this.id = id;
        this.type = type;
        this.min = min;
        this.max = max;
        this.seed = seed;
        this.capacity = capacity;
        this.spots = new ArrayList<>(spots);
    }

    /** Bounds are padded by one block so walls, beds and doors count as "inside". */
    public boolean contains(BlockPos p) {
        return p.getX() >= min.getX() - 1 && p.getX() <= max.getX() + 1
                && p.getY() >= min.getY() - 1 && p.getY() <= max.getY() + 1
                && p.getZ() >= min.getZ() - 1 && p.getZ() <= max.getZ() + 1;
    }

    /** Each NPC gets its own stable spot so they don't all stack on one block. */
    public BlockPos spotFor(String npcId) {
        if (spots.isEmpty()) return seed;
        return spots.get(Math.floorMod(npcId.hashCode(), spots.size()));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("type", type);
        t.putLong("min", min.asLong());
        t.putLong("max", max.asLong());
        t.putLong("seed", seed.asLong());
        t.putInt("capacity", capacity);
        t.putLongArray("spots", spots.stream().mapToLong(BlockPos::asLong).toArray());
        return t;
    }

    public static Building load(CompoundTag t) {
        List<BlockPos> spots = new ArrayList<>();
        for (long l : t.getLongArray("spots")) spots.add(BlockPos.of(l));
        return new Building(t.getUUID("id"), t.getString("type"), BlockPos.of(t.getLong("min")),
                BlockPos.of(t.getLong("max")), BlockPos.of(t.getLong("seed")), t.getInt("capacity"), spots);
    }
}
