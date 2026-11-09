package com.hearthvale.entity.ai;

import com.hearthvale.data.NpcDefinition;
import com.hearthvale.data.ScheduleEntry;
import com.hearthvale.entity.NpcEntity;
import com.hearthvale.util.GameTime;
import com.hearthvale.util.Ids;
import com.hearthvale.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The heart of the per-NPC pathing: every few seconds the NPC works out where its schedule says it should be
 * (home / work / hearth / a building type / wandering), then walks there with vanilla pathfinding (opening doors).
 * If it gets stuck or the chunk was unloaded for a long time it is teleported to the destination.
 */
public class NpcScheduleGoal extends Goal {
    private final NpcEntity npc;
    private BlockPos anchor;
    private double radius = 4;
    private boolean wander;
    private int recompute, idle, stuck, repath;

    public NpcScheduleGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !npc.level().isClientSide && !npc.isTalking() && !npc.isCutsceneControlled() && !npc.getNpcId().isEmpty();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() { recompute = 0; stuck = 0; }

    @Override
    public void stop() { npc.getNavigation().stop(); }

    @Override
    public void tick() {
        if (--recompute <= 0) {
            recompute = 100;
            resolve();
        }
        if (anchor == null) return;

        Vec3 target = Vec3.atBottomCenterOf(anchor);
        double distSq = npc.distanceToSqr(target);
        PathNavigation nav = npc.getNavigation();

        if (distSq > 70 * 70 && noPlayerNear(24)) {
            teleportToAnchor();
            return;
        }

        if (wander) {
            double far = radius + 3;
            if (distSq > far * far) {
                if (--repath <= 0) {
                    repath = 20;
                    nav.moveTo(target.x, target.y, target.z, 1.0);
                }
            } else if (nav.isDone() && --idle <= 0) {
                double a = npc.getRandom().nextDouble() * Math.PI * 2;
                double d = npc.getRandom().nextDouble() * radius;
                nav.moveTo(target.x + Math.cos(a) * d, target.y, target.z + Math.sin(a) * d, 0.6);
                idle = 40 + npc.getRandom().nextInt(120);
            }
            return;
        }

        if (distSq > 2.25) {
            stuck++;
            if (nav.isDone() && --repath <= 0) {
                repath = 20;
                if (!nav.moveTo(target.x, target.y, target.z, 1.0)) stuck += 10;
            }
            if ((stuck > 600 && noPlayerNear(16)) || stuck > 2400) {
                teleportToAnchor();
                stuck = 0;
            }
        } else {
            stuck = 0;
            nav.stop();
        }
    }

    private boolean noPlayerNear(double dist) {
        return npc.level().getNearestPlayer(npc, dist) == null;
    }

    private void teleportToAnchor() {
        npc.getNavigation().stop();
        npc.moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, npc.getYRot(), 0f);
    }

    /** Works out anchor/radius/wander from NPC state + schedule. */
    private void resolve() {
        ServerLevel level = (ServerLevel) npc.level();
        Village v = npc.village();
        NpcDefinition def = npc.def();
        if (v == null || def == null) {
            anchor = null;
            return;
        }
        int state = npc.getNpcState();
        if (state == NpcEntity.STATE_ARRIVING) { setHearth(v, 3, false); return; }
        if (state == NpcEntity.STATE_WAITING) { setHearth(v, 4, true); return; }

        Resident r = v.residents.get(def.id);
        WorldState ws = WorldState.get(level);
        List<ScheduleEntry> schedule = ws.spouseOf(def.id) != null ? def.spouseSchedule : def.schedule;
        int tick = GameTime.tick(level);
        int weekday = GameTime.weekday(level);
        String at = "hearth";
        for (ScheduleEntry e : schedule) {
            if (e.matches(tick, level.isRaining(), weekday)) {
                at = e.at();
                break;
            }
        }

        switch (at) {
            case "wander" -> setHearth(v, 14, true);
            case "hearth" -> setHearth(v, 4, true);
            case "home" -> setBuilding(v, r == null ? null : v.buildings.get(r.home), def);
            case "work" -> setBuilding(v, r == null ? null : v.buildings.get(r.work), def);
            default -> {
                if (at.startsWith("building:")) {
                    List<Building> list = v.ofType(Ids.norm(at.substring(9)));
                    if (list.isEmpty()) setHearth(v, 4, true);
                    else setBuilding(v, list.get(Math.floorMod(def.id.hashCode(), list.size())), def);
                } else {
                    setHearth(v, 4, true);
                }
            }
        }
    }

    private void setHearth(Village v, double radius, boolean wander) {
        this.anchor = v.hearth.above();
        this.radius = radius;
        this.wander = wander;
    }

    private void setBuilding(Village v, Building b, NpcDefinition def) {
        if (b == null) {
            setHearth(v, 4, true);
            return;
        }
        this.anchor = b.spotFor(def.id);
        this.radius = 0;
        this.wander = false;
    }
}
