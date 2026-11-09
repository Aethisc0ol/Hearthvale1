package com.hearthvale.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Everything Hearthvale remembers about one player. */
public class PlayerData {
    public final Map<String, Relationship> relationships = new LinkedHashMap<>();
    public final Set<String> flags = new HashSet<>();

    /** Get-or-create (marks the NPC as "met"). */
    public Relationship rel(String npcId) {
        return relationships.computeIfAbsent(npcId, k -> new Relationship());
    }

    /** Get without creating. Returns a throwaway object if the player hasn't met the NPC. */
    public Relationship peek(String npcId) {
        Relationship r = relationships.get(npcId);
        return r != null ? r : new Relationship();
    }

    /** NPC id of whoever this player is engaged or married to, or null. */
    public String partner() {
        for (Map.Entry<String, Relationship> e : relationships.entrySet()) {
            Relationship.Status s = e.getValue().status;
            if (s == Relationship.Status.ENGAGED || s == Relationship.Status.MARRIED) return e.getKey();
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag rels = new ListTag();
        relationships.forEach((id, r) -> {
            CompoundTag rt = r.save();
            rt.putString("npc", id);
            rels.add(rt);
        });
        t.put("relationships", rels);
        ListTag fl = new ListTag();
        for (String f : flags) fl.add(StringTag.valueOf(f));
        t.put("flags", fl);
        return t;
    }

    public static PlayerData load(CompoundTag t) {
        PlayerData d = new PlayerData();
        ListTag rels = t.getList("relationships", Tag.TAG_COMPOUND);
        for (int i = 0; i < rels.size(); i++) {
            CompoundTag rt = rels.getCompound(i);
            d.relationships.put(rt.getString("npc"), Relationship.load(rt));
        }
        ListTag fl = t.getList("flags", Tag.TAG_STRING);
        for (int i = 0; i < fl.size(); i++) d.flags.add(fl.getString(i));
        return d;
    }
}
