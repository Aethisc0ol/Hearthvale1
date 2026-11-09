package com.hearthvale.world;

import com.hearthvale.Config;
import com.hearthvale.ModRegistry;
import com.hearthvale.data.BuildingRegistry;
import com.hearthvale.data.BuildingType;
import com.hearthvale.data.NpcDefinition;
import com.hearthvale.data.NpcRegistry;
import com.hearthvale.entity.NpcEntity;
import com.hearthvale.util.Fonts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Founding villages, registering buildings, NPC arrival, housing assignment. */
public final class VillageManager {
    private static final String[] SUFFIXES = {"Hamlet", "Hollow", "Glen", "Rest", "Haven"};

    private VillageManager() {}

    private static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Fonts.text(text).withStyle(color));
    }

    // ---------------------------------------------------------------- founding

    /** Returns an error message, or null on success. */
    public static String found(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        WorldState ws = WorldState.get(level);
        int min = Config.MIN_VILLAGE_DISTANCE.get();
        for (Village v : ws.villages()) {
            if (v.dim.equals(level.dimension()) && v.hearth.distSqr(pos) < (double) min * min) {
                return "Too close to \"" + v.name + "\" - villages need " + min + " blocks between hearths.";
            }
        }
        String base = player != null ? player.getGameProfile().getName() + "'s" : "Little";
        Village v = new Village(UUID.randomUUID(), base + " " + SUFFIXES[level.random.nextInt(SUFFIXES.length)],
                level.dimension(), pos.immutable(), player != null ? player.getUUID() : null);
        v.lastArrival = level.getGameTime();
        ws.addVillage(v);
        if (player != null) {
            msg(player, "Village founded: " + v.name + "!", ChatFormatting.GOLD);
            msg(player, "Build rooms within " + Config.VILLAGE_RADIUS.get() + " blocks, then stand inside each and use a Surveyor's Deed.", ChatFormatting.GRAY);
        }
        return null;
    }

    public static void dissolve(ServerLevel level, BlockPos pos) {
        WorldState ws = WorldState.get(level);
        Village found = null;
        for (Village v : ws.villages()) if (v.dim.equals(level.dimension()) && v.hearth.equals(pos)) found = v;
        if (found == null) return;
        ws.removeVillage(found.id);
        for (Resident r : found.residents.values()) {
            Entity e = level.getEntity(r.entity);
            if (e != null) e.discard();
        }
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(pos) < 128 * 128) {
                msg(p, found.name + " has been dissolved. Its villagers packed up and left.", ChatFormatting.YELLOW);
            }
        }
    }

    // ---------------------------------------------------------------- buildings

    public static void survey(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        WorldState ws = WorldState.get(level);
        Village v = ws.nearest(level.dimension(), player.blockPosition(), Config.VILLAGE_RADIUS.get());
        if (v == null) {
            msg(player, "No village nearby. Place a Village Hearth first (within " + Config.VILLAGE_RADIUS.get() + " blocks).", ChatFormatting.RED);
            return;
        }
        BuildingScanner.Scan scan = BuildingScanner.scan(level, player.blockPosition());
        if (!scan.ok()) {
            msg(player, scan.error(), ChatFormatting.RED);
            return;
        }
        BuildingRegistry.Match m = BuildingRegistry.classify(scan.interior().size(), scan.furnishings());
        if (m.type() == null) {
            String hint = m.closest() == null ? "" : " Closest: " + m.closest().displayName + " - needs " + String.join(", ", m.missing()) + ".";
            msg(player, "This room doesn't match any building type yet." + hint, ChatFormatting.YELLOW);
            return;
        }

        BuildingType type = m.type();
        int beds = 0;
        for (BlockState st : scan.furnishings()) if (st.getBlock() instanceof BedBlock) beds++;
        int capacity = type.housing ? Math.max(1, beds) : 0;

        Set<Long> interiorSet = new HashSet<>();
        for (BlockPos p : scan.interior()) interiorSet.add(p.asLong());
        Building existing = null;
        for (Building b : v.buildings.values()) {
            if (interiorSet.contains(b.seed.asLong()) || b.contains(player.blockPosition())) {
                existing = b;
                break;
            }
        }

        boolean updated = existing != null;
        if (existing == null) {
            existing = new Building(UUID.randomUUID(), type.id, scan.min(), scan.max(), scan.seed(), capacity, scan.spots());
            v.buildings.put(existing.id, existing);
        } else {
            existing.type = type.id;
            existing.min = scan.min();
            existing.max = scan.max();
            existing.seed = scan.seed();
            existing.capacity = capacity;
            existing.spots = new ArrayList<>(scan.spots());
        }
        refreshAssignments(v);
        ws.setDirty();
        msg(player, (updated ? "Updated: " : "Registered: ") + type.displayName
                + (type.housing ? " (sleeps " + capacity + ")" : "") + " in " + v.name + ".", ChatFormatting.GREEN);
    }

    /** Periodic re-check: buildings that were dismantled stop counting. */
    private static void revalidate(ServerLevel level, Village v, MinecraftServer server) {
        List<UUID> remove = new ArrayList<>();
        for (Building b : v.buildings.values()) {
            if (!level.hasChunkAt(b.seed)) continue;
            BuildingScanner.Scan s = BuildingScanner.scan(level, b.seed);
            BuildingRegistry.Match m = s.ok() ? BuildingRegistry.classify(s.interior().size(), s.furnishings()) : null;
            if (m == null || m.type() == null) {
                remove.add(b.id);
                continue;
            }
            int beds = 0;
            for (BlockState st : s.furnishings()) if (st.getBlock() instanceof BedBlock) beds++;
            b.type = m.type().id;
            b.min = s.min();
            b.max = s.max();
            b.spots = new ArrayList<>(s.spots());
            b.capacity = m.type().housing ? Math.max(1, beds) : 0;
        }
        if (!remove.isEmpty()) {
            for (UUID id : remove) v.buildings.remove(id);
            ServerPlayer owner = v.owner == null ? null : server.getPlayerList().getPlayer(v.owner);
            if (owner != null) msg(owner, remove.size() + " building(s) in " + v.name + " no longer qualify and were removed.", ChatFormatting.YELLOW);
        }
        refreshAssignments(v);
        WorldState.get(server).setDirty();
    }

    // ---------------------------------------------------------------- housing

    public static Building findFreeHome(Village v, String homeType, String npcId) {
        if (homeType == null) return null;
        for (Building b : v.ofType(homeType)) {
            int used = 0;
            for (Resident r : v.residents.values()) if (b.id.equals(r.home) && !r.npcId.equals(npcId)) used++;
            if (used < b.capacity) return b;
        }
        return null;
    }

    public static void assignHousing(Village v, Resident r, NpcDefinition d) {
        Building home = r.home == null ? null : v.buildings.get(r.home);
        if (home == null || (d.homeType != null && !home.type.equals(d.homeType) && v.occupants(home) > home.capacity + 1)) {
            r.home = null;
            Building b = findFreeHome(v, d.homeType, r.npcId);
            if (b != null) r.home = b.id;
        }
        Building work = r.work == null ? null : v.buildings.get(r.work);
        if (d.workType != null && (work == null || !work.type.equals(d.workType))) {
            r.work = null;
            List<Building> list = v.ofType(d.workType);
            if (!list.isEmpty()) r.work = list.get(Math.floorMod(d.id.hashCode(), list.size())).id;
        }
    }

    public static void refreshAssignments(Village v) {
        for (Resident r : v.residents.values()) {
            if (!r.settled) continue;
            NpcDefinition d = NpcRegistry.get(r.npcId);
            if (d != null) assignHousing(v, r, d);
        }
    }

    /** After marriage the spouse moves into the home where the player sleeps. */
    public static void moveInWithPlayer(NpcEntity npc, ServerPlayer player) {
        Village v = npc.village();
        if (v == null) return;
        Resident r = v.residents.get(npc.getNpcId());
        if (r == null) return;
        BlockPos bed = player.getRespawnPosition();
        if (bed != null && player.getRespawnDimension().equals(v.dim)) {
            Building b = v.buildingAt(bed);
            if (b != null) r.home = b.id;
        }
        WorldState.get(player.serverLevel()).setDirty();
    }

    // ---------------------------------------------------------------- arrivals & ticking

    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % 100 != 0) return;
        WorldState ws = WorldState.get(server);
        for (Village v : new ArrayList<>(ws.villages())) {
            ServerLevel level = server.getLevel(v.dim);
            if (level == null || !level.hasChunkAt(v.hearth)) continue;
            long now = level.getGameTime();
            if (now - v.lastArrival >= Config.ARRIVAL_INTERVAL.get()) {
                v.lastArrival = now;
                tryArrival(level, v, null);
                ws.setDirty();
            }
            if (t % 6000 == 0) revalidate(level, v, server);
        }
    }

    private static boolean arrivalReady(Village v, NpcDefinition d) {
        for (String req : d.requires) if (!v.hasType(req)) return false;
        if (d.workType != null && !v.hasType(d.workType)) return false;
        return d.homeType == null || findFreeHome(v, d.homeType, d.id) != null;
    }

    /** forcedNpc != null skips all requirement checks (used by the /hearthvale arrive command). */
    public static NpcEntity tryArrival(ServerLevel level, Village v, @Nullable String forcedNpc) {
        BlockPos h = v.hearth;
        if (level.getNearestPlayer(h.getX(), h.getY(), h.getZ(), 128, false) == null) return null;
        WorldState ws = WorldState.get(level);
        List<NpcDefinition> eligible = new ArrayList<>();
        for (NpcDefinition d : NpcRegistry.all()) {
            if (forcedNpc != null && !d.id.equals(forcedNpc)) continue;
            if (ws.villageOfNpc(d.id) != null) continue;
            if (forcedNpc == null && !arrivalReady(v, d)) continue;
            eligible.add(d);
        }
        if (eligible.isEmpty()) return null;
        NpcDefinition pick = eligible.get(level.random.nextInt(eligible.size()));
        NpcEntity npc = spawnWanderer(level, v, pick);
        if (npc == null) return null;
        v.residents.put(pick.id, new Resident(pick.id, npc.getUUID()));
        ws.setDirty();
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(h) < 160 * 160) {
                msg(p, "A traveler named " + pick.name + " is approaching " + v.name + "...", ChatFormatting.YELLOW);
            }
        }
        return npc;
    }

    private static NpcEntity spawnWanderer(ServerLevel level, Village v, NpcDefinition d) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double ang = level.random.nextDouble() * Math.PI * 2;
            int dist = attempt < 7 ? 32 + level.random.nextInt(16) : 12;
            BlockPos p = v.hearth.offset((int) (Math.cos(ang) * dist), 0, (int) (Math.sin(ang) * dist));
            if (!level.hasChunkAt(p)) continue;
            p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
            if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.below()).isEmpty()) continue;
            NpcEntity npc = ModRegistry.NPC.get().create(level);
            if (npc == null) return null;
            npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            npc.setNpcId(d.id);
            npc.setVillageId(v.id);
            npc.setNpcState(NpcEntity.STATE_ARRIVING);
            level.addFreshEntity(npc);
            return npc;
        }
        return null;
    }

    /** The player welcomed the wanderer: they move into a home and start their daily routine. */
    public static void settle(NpcEntity npc, ServerPlayer player) {
        Village v = npc.village();
        NpcDefinition d = npc.def();
        if (v == null || d == null) return;
        Resident r = v.residents.get(d.id);
        if (r == null) {
            r = new Resident(d.id, npc.getUUID());
            v.residents.put(d.id, r);
        }
        if (npc.getNpcState() == NpcEntity.STATE_RESIDENT) return;
        r.settled = true;
        assignHousing(v, r, d);
        npc.setNpcState(NpcEntity.STATE_RESIDENT);
        WorldState.get(player.serverLevel()).setDirty();
        msg(player, d.name + " has settled in " + v.name + "!" + (r.home == null && d.homeType != null ? " (No free home yet - they'll sleep by the hearth.)" : ""), ChatFormatting.GREEN);
    }

    public static void onNpcRemoved(NpcEntity npc) {
        UUID vid = npc.getVillageId();
        if (vid == null || !(npc.level() instanceof ServerLevel sl)) return;
        WorldState ws = WorldState.get(sl);
        Village v = ws.village(vid);
        if (v == null) return;
        Resident r = v.residents.get(npc.getNpcId());
        if (r != null && r.entity.equals(npc.getUUID())) {
            v.residents.remove(npc.getNpcId());
            ws.setDirty();
        }
    }

    public static void describe(ServerPlayer player, Village v) {
        msg(player, "== " + v.name + " ==", ChatFormatting.GOLD);
        Map<String, Integer> counts = new TreeMap<>();
        for (Building b : v.buildings.values()) {
            BuildingType t = BuildingRegistry.get(b.type);
            counts.merge(t == null ? b.type : t.displayName, 1, Integer::sum);
        }
        msg(player, counts.isEmpty() ? "No buildings registered yet." : "Buildings: " + counts, ChatFormatting.GRAY);
        List<String> names = new ArrayList<>();
        for (Resident r : v.residents.values()) {
            NpcDefinition d = NpcRegistry.get(r.npcId);
            names.add((d == null ? r.npcId : d.name) + (r.settled ? "" : " (arriving)"));
        }
        msg(player, names.isEmpty() ? "No residents yet." : "Residents: " + String.join(", ", names), ChatFormatting.GRAY);
    }
}
