package com.hearthvale.book;

import com.hearthvale.Config;
import com.hearthvale.data.BuildingRegistry;
import com.hearthvale.data.BuildingType;
import com.hearthvale.data.NpcDefinition;
import com.hearthvale.data.NpcRegistry;
import com.hearthvale.world.PlayerData;
import com.hearthvale.world.Relationship;
import com.hearthvale.world.WorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.LinkedHashSet;
import java.util.Set;

/** Packs everything the Hearthvale book needs (NPC profiles, requirements, this player's friendship) into one tag. */
public final class BookDataBuilder {
    private BookDataBuilder() {}

    public static CompoundTag build(ServerPlayer player) {
        PlayerData pd = WorldState.get(player.serverLevel()).player(player.getUUID());
        CompoundTag root = new CompoundTag();
        ListTag npcs = new ListTag();
        Set<String> buildingIds = new LinkedHashSet<>();

        for (NpcDefinition d : NpcRegistry.all()) {
            Relationship r = pd.relationships.get(d.id);
            boolean met = r != null;
            if (r == null) r = new Relationship();

            CompoundTag n = new CompoundTag();
            n.putString("id", d.id);
            n.putString("name", d.name);
            n.putBoolean("slim", d.slim);
            n.putBoolean("romance", d.romanceable);
            n.putString("birthday", d.birthdayLabel());
            n.putString("summary", d.summary);
            n.putString("backstory", d.backstory);
            n.putString("home", d.homeType == null ? "" : d.homeType);
            n.putString("work", d.workType == null ? "" : d.workType);
            ListTag req = new ListTag();
            for (String b : d.requires) {
                req.add(StringTag.valueOf(b));
                buildingIds.add(b);
            }
            n.put("requires", req);
            if (d.homeType != null) buildingIds.add(d.homeType);
            if (d.workType != null) buildingIds.add(d.workType);
            n.putInt("points", r.points);
            n.putString("status", r.status.name());
            n.putBoolean("met", met);

            ListTag events = new ListTag();
            for (NpcDefinition.NpcEvent ev : d.events) {
                CompoundTag e = new CompoundTag();
                e.putString("id", ev.id());
                e.putString("name", ev.name());
                e.putBoolean("approach", ev.approach());
                e.putBoolean("seen", r.events.contains(ev.id()));
                ListTag reqs = new ListTag();
                for (String line : ev.cond().describe(d)) reqs.add(StringTag.valueOf(line));
                e.put("reqs", reqs);
                events.add(e);
            }
            n.put("events", events);
            npcs.add(n);
        }
        root.put("npcs", npcs);

        ListTag buildings = new ListTag();
        for (String id : buildingIds) {
            BuildingType t = BuildingRegistry.get(id);
            if (t == null) continue;
            CompoundTag b = new CompoundTag();
            b.putString("id", t.id);
            b.putString("name", t.displayName);
            b.putInt("minVolume", t.minVolume);
            b.putBoolean("housing", t.housing);
            ListTag reqs = new ListTag();
            for (BuildingType.Req r : t.reqs) {
                CompoundTag rt = new CompoundTag();
                ListTag any = new ListTag();
                for (String a : r.any()) any.add(StringTag.valueOf(a));
                rt.put("any", any);
                rt.putInt("count", r.count());
                reqs.add(rt);
            }
            b.put("reqs", reqs);
            buildings.add(b);
        }
        root.put("buildings", buildings);

        CompoundTag settings = new CompoundTag();
        settings.putBoolean("op", canEdit(player));
        settings.putInt("arrivalMinutes", Config.ARRIVAL_INTERVAL.get() / 1200);
        settings.putBoolean("autoEvents", Config.AUTO_EVENTS.get());
        settings.putInt("maxVolume", Config.MAX_VOLUME.get());
        root.put("settings", settings);
        return root;
    }

    /** Operators (and the owner of a single-player world) may change village settings. */
    public static boolean canEdit(ServerPlayer p) {
        return p.hasPermissions(2) || (p.getServer() != null && p.getServer().isSingleplayerOwner(p.getGameProfile()));
    }

    public static void applySetting(ServerPlayer p, String key, int value) {
        if (!canEdit(p)) return;
        switch (key) {
            case "arrivalMinutes" -> Config.ARRIVAL_INTERVAL.set(Mth.clamp(value, 1, 120) * 1200);
            case "autoEvents" -> Config.AUTO_EVENTS.set(value != 0);
            case "maxVolume" -> Config.MAX_VOLUME.set(Mth.clamp(value, 100, 6000));
            default -> { return; }
        }
        Config.SPEC.save();
    }
}
