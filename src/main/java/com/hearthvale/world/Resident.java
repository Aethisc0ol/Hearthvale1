package com.hearthvale.world;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Village-side record of an NPC that arrived (and maybe settled). */
public class Resident {
    public final String npcId;
    public UUID entity;
    public UUID home, work;
    public boolean settled;

    public Resident(String npcId, UUID entity) {
        this.npcId = npcId;
        this.entity = entity;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("npc", npcId);
        t.putUUID("entity", entity);
        if (home != null) t.putUUID("home", home);
        if (work != null) t.putUUID("work", work);
        t.putBoolean("settled", settled);
        return t;
    }

    public static Resident load(CompoundTag t) {
        Resident r = new Resident(t.getString("npc"), t.getUUID("entity"));
        if (t.hasUUID("home")) r.home = t.getUUID("home");
        if (t.hasUUID("work")) r.work = t.getUUID("work");
        r.settled = t.getBoolean("settled");
        return r;
    }
}
