package com.hearthvale.cutscene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthvale.Hearthvale;
import com.hearthvale.ModRegistry;
import com.hearthvale.data.EffectContext;
import com.hearthvale.data.NpcDefinition;
import com.hearthvale.data.NpcRegistry;
import com.hearthvale.data.Script;
import com.hearthvale.data.ScriptEffect;
import com.hearthvale.dialogue.DialogueManager;
import com.hearthvale.dialogue.DialogueSession;
import com.hearthvale.dialogue.NpcInteractions;
import com.hearthvale.entity.NpcEntity;
import com.hearthvale.network.*;
import com.hearthvale.util.Ids;
import com.hearthvale.util.Json;
import com.hearthvale.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Interprets a {@link Cutscene} for one player, one server tick at a time.
 * Steps run in order; a step with "wait": false runs in the background (e.g. an NPC walking while the camera pans);
 * a "sync" step waits for all background steps.
 */
public class CutsceneRunner {

    interface Task {
        /** @return true when finished */
        boolean tick();
        /** Jump straight to the end state (used when skipping). */
        default void complete() {}
    }

    private static final class Frame {
        final List<Cutscene.Step> steps;
        int idx = 0;
        Frame(List<Cutscene.Step> steps) { this.steps = steps; }
    }

    final ServerPlayer player;
    final NpcEntity npc;
    final NpcDefinition def;
    final Cutscene cs;
    private final ServerLevel level;

    private final Deque<Frame> stack = new ArrayDeque<>();
    private final List<Task> active = new ArrayList<>();
    private Task blocking;
    private final Map<String, Entity> actors = new HashMap<>();
    final List<NpcEntity> extras = new ArrayList<>();
    private final List<NpcEntity> controlled = new ArrayList<>();
    private final Map<NpcEntity, Vec3> restorePos = new LinkedHashMap<>();
    private final Map<NpcEntity, ItemStack> restoreHeld = new LinkedHashMap<>();

    private Vec3 origin;
    private Vec3 playerStartPos;
    private float playerStartYaw, playerStartPitch;
    private boolean playerMoved, fast, finished;
    private int age;
    int pendingBranch = -1;

    CutsceneRunner(ServerPlayer player, NpcEntity npc, NpcDefinition def, Cutscene cs) {
        this.player = player;
        this.npc = npc;
        this.def = def;
        this.cs = cs;
        this.level = player.serverLevel();
    }

    // ------------------------------------------------------------------ lifecycle

    void begin() {
        origin = resolveOrigin();
        playerStartPos = player.position();
        playerStartYaw = player.getYRot();
        playerStartPitch = player.getXRot();
        control(npc, false);
        actors.put("npc", npc);
        actors.put("player", player);
        for (Map.Entry<String, String> a : cs.actors.entrySet()) actors.put(a.getKey(), findActor(a.getValue()));
        if (cs.playerStart != null) {
            teleportPlayer(pos(cs.playerStart), player.getYRot(), player.getXRot());
            playerMoved = true;
        }
        send(new CutsceneStatePayload(true, cs.skippable, true));
        stack.push(new Frame(cs.steps));
    }

    void tick() {
        if (finished) return;
        age++;
        if (!player.isAlive() || player.hasDisconnected() || npc.isRemoved() || npc.level() != player.level()) {
            abort();
            return;
        }
        active.removeIf(Task::tick);
        if (blocking != null) {
            if (blocking.tick()) blocking = null;
            else return;
        }
        int guard = 0;
        while (!finished && blocking == null && guard++ < 500) {
            Cutscene.Step s = next();
            if (s == null) {
                if (active.isEmpty()) finish(true);
                return;
            }
            Task t = safeExec(s);
            if (t != null) {
                if (s.wait) blocking = t;
                else active.add(t);
            }
        }
    }

    /** ESC: jump to the end, applying every remaining effect. */
    void skip() {
        if (finished || fast) return;
        fast = true;
        DialogueSession ds = DialogueManager.get(player.getUUID());
        if (ds != null) ds.fastForward();
        for (Task t : new ArrayList<>(active)) t.complete();
        active.clear();
        if (blocking != null) {
            blocking.complete();
            blocking = null;
        }
        int guard = 0;
        while (!finished && guard++ < 20000) {
            Cutscene.Step s = next();
            if (s == null) break;
            Task t = safeExec(s);
            if (t != null) t.complete();
        }
        finish(true);
    }

    void abort() { finish(false); }

    private void finish(boolean completed) {
        if (finished) return;
        finished = true;
        DialogueManager.end(player.getUUID());
        boolean online = !player.hasDisconnected();
        if (online && cs.returnPlayer && playerMoved) teleportPlayer(playerStartPos, playerStartYaw, playerStartPitch);
        for (Map.Entry<NpcEntity, ItemStack> e : restoreHeld.entrySet()) e.getKey().setItemSlot(EquipmentSlot.MAINHAND, e.getValue());
        for (NpcEntity e : controlled) {
            e.setCutsceneControlled(false);
            e.getNavigation().stop();
            Vec3 back = restorePos.get(e);
            if (back != null && cs.restoreNpcs && !e.isRemoved()) e.moveTo(back.x, back.y, back.z, e.getYRot(), 0f);
        }
        for (NpcEntity e : extras) e.discard();
        npc.endTalk();
        if (online) send(new CutsceneStatePayload(false, false, false));
        CutsceneManager.remove(this);
    }

    private Cutscene.Step next() {
        while (!stack.isEmpty()) {
            Frame f = stack.peek();
            if (f.idx >= f.steps.size()) {
                stack.pop();
                continue;
            }
            return f.steps.get(f.idx++);
        }
        return null;
    }

    private Task safeExec(Cutscene.Step s) {
        try {
            return exec(s);
        } catch (Exception e) {
            Hearthvale.LOGGER.error("Cutscene step '{}' failed ({}): {}", s.type, s.json, e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------ step execution

    private Task exec(Cutscene.Step s) {
        JsonObject o = s.json;
        switch (s.type) {
            case "talk": return execTalk(s);
            case "wait": return fast ? null : new WaitTask(ticks(o, 20));
            case "sync": return fast ? null : (Task) () -> active.isEmpty();
            case "fade": {
                if (fast) return null;
                String to = Json.str(o, "to", "black");
                int color = -1;
                float alpha = 1f;
                switch (to) {
                    case "clear" -> alpha = 0f;
                    case "white" -> color = 0xFFFFFF;
                    case "black" -> color = 0x000000;
                    default -> { if (to.startsWith("#")) color = Integer.parseInt(to.substring(1), 16) & 0xFFFFFF; }
                }
                int t = ticks(o, 20);
                send(new FadePayload(color, alpha, t));
                return t > 0 ? new WaitTask(t) : null;
            }
            case "letterbox":
                send(new CutsceneStatePayload(true, cs.skippable, Json.bool(o, "on", true)));
                return null;
            case "title": {
                if (fast) return null;
                int t = ticks(o, 60);
                send(new TitlePayload(Json.str(o, "text", ""), t));
                return new WaitTask(t);
            }
            case "camera": return execCamera(o);
            case "move": return execMove(o);
            case "warp": {
                Entity a = actor(o);
                Vec3 to = pos(o.get("to"));
                if (a instanceof NpcEntity ne) teleportNpc(ne, safeSpot(to));
                else if (a == player) {
                    playerMoved = true;
                    teleportPlayer(to, player.getYRot(), player.getXRot());
                }
                if (o.has("face")) faceToward(a, pos(o.get("face")));
                return null;
            }
            case "face": {
                Entity a = actor(o);
                if (o.has("direction")) {
                    float yaw = switch (Json.str(o, "direction", "south").toLowerCase()) {
                        case "north" -> 180f;
                        case "east" -> -90f;
                        case "west" -> 90f;
                        default -> 0f;
                    };
                    setFacing(a, yaw, 0f);
                } else {
                    faceToward(a, pos(o.get("toward")));
                }
                return null;
            }
            case "emote": {
                if (fast) return null;
                Entity a = actor(o);
                level.sendParticles(emoteParticle(Json.str(o, "emote", "heart")), a.getX(), a.getY() + a.getBbHeight() + 0.3, a.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
                return null;
            }
            case "sound": {
                if (fast) return null;
                ResourceLocation rl = ResourceLocation.tryParse(Json.str(o, "sound", ""));
                SoundEvent se = rl == null ? null : BuiltInRegistries.SOUND_EVENT.get(rl);
                if (se == null) throw new IllegalArgumentException("unknown sound '" + Json.str(o, "sound", "") + "'");
                Vec3 at = o.has("at") ? pos(o.get("at")) : npc.position();
                level.playSound(null, at.x, at.y, at.z, se, SoundSource.NEUTRAL, (float) Json.dbl(o, "volume", 1.0), (float) Json.dbl(o, "pitch", 1.0));
                return null;
            }
            case "animate": return execAnimate(o);
            case "hold": {
                Entity a = actor(o);
                if (!(a instanceof NpcEntity ne)) return null;
                String item = Json.str(o, "item", "none");
                ItemStack st = ItemStack.EMPTY;
                if (!item.equals("none") && !item.isEmpty()) {
                    Item it = BuiltInRegistries.ITEM.get(Ids.rl(item));
                    st = new ItemStack(it);
                }
                restoreHeld.putIfAbsent(ne, ne.getItemBySlot(EquipmentSlot.MAINHAND).copy());
                ne.setItemSlot(EquipmentSlot.MAINHAND, st);
                return null;
            }
            case "spawn": {
                String name = Json.str(o, "actor", "extra");
                NpcEntity e = spawnTemp(Json.str(o, "skin", "hearthvale:traveler"), Json.str(o, "name", name), safeSpot(pos(o.get("at"))));
                actors.put(name, e);
                if (o.has("face")) faceToward(e, pos(o.get("face")));
                return null;
            }
            case "despawn": {
                Entity e = actors.remove(Json.str(o, "actor", ""));
                if (e instanceof NpcEntity ne && extras.remove(ne)) {
                    controlled.remove(ne);
                    ne.discard();
                }
                return null;
            }
            case "effect": {
                EffectContext ctx = new EffectContext(player, npc, def);
                for (ScriptEffect e : s.effects) e.apply(ctx);
                return null;
            }
            case "if": {
                PlayerData pd = WorldState.get(level).player(player.getUUID());
                Relationship rel = pd.rel(def.id);
                boolean ok = s.cond.test(NpcInteractions.ctx(level, npc, def, pd, rel));
                List<Cutscene.Step> branch = ok ? s.then : s.otherwise;
                if (!branch.isEmpty()) stack.push(new Frame(branch));
                return null;
            }
            case "end":
                finish(true);
                return null;
            default:
                return null;
        }
    }

    private Task execTalk(Cutscene.Step s) {
        pendingBranch = -1;
        if (fast) {
            fastRun(s.script);
            applyBranch(s);
            return null;
        }
        DialogueManager.start(player, npc, s.script, null);
        return new TalkTask(s);
    }

    private void fastRun(Script script) {
        EffectContext ctx = new EffectContext(player, npc, def);
        for (Script.Line l : script.lines()) {
            for (ScriptEffect e : l.effects()) e.apply(ctx);
            if (!l.choices().isEmpty()) {
                Script.Choice c = l.choices().get(0);
                for (ScriptEffect e : c.effects()) e.apply(ctx);
                fastRun(c.reply());
            }
        }
    }

    private void applyBranch(Cutscene.Step s) {
        if (s.branches != null && pendingBranch >= 0 && pendingBranch < s.branches.size()) {
            List<Cutscene.Step> b = s.branches.get(pendingBranch);
            if (!b.isEmpty()) stack.push(new Frame(b));
        }
        pendingBranch = -1;
    }

    private Task execCamera(JsonObject o) {
        if (fast) return null;
        if (Json.bool(o, "reset", false)) {
            send(CameraPayload.reset());
            return null;
        }
        boolean hasPos = o.has("to");
        Vec3 to = hasPos ? pos(o.get("to")) : Vec3.ZERO;
        int lookType = 0, eid = -1;
        float yaw = 0, pitch = 0;
        Vec3 lp = Vec3.ZERO;
        if (o.has("look")) {
            JsonElement l = o.get("look");
            if (l.isJsonObject() && (l.getAsJsonObject().has("yaw") || l.getAsJsonObject().has("pitch"))) {
                lookType = 1;
                yaw = (float) Json.dbl(l.getAsJsonObject(), "yaw", 0);
                pitch = (float) Json.dbl(l.getAsJsonObject(), "pitch", 0);
            } else if (l.isJsonPrimitive() && actors.containsKey(l.getAsString())) {
                Entity e = actors.get(l.getAsString());
                lookType = 3;
                eid = e.getId();
                lp = e.position();
            } else {
                lookType = 2;
                lp = pos(l);
            }
        }
        int t = Json.bool(o, "snap", false) ? 0 : ticks(o, 40);
        send(new CameraPayload(0, hasPos, to.x, to.y, to.z, lookType, yaw, pitch, lp.x, lp.y, lp.z, eid, t, !"linear".equals(Json.str(o, "ease", "smooth"))));
        return t > 0 ? new WaitTask(t) : null;
    }

    private Task execMove(JsonObject o) {
        Entity a = actor(o);
        Vec3 to = pos(o.get("to"));
        double within = Json.dbl(o, "within", 0.9);
        double speed = Json.dbl(o, "speed", 1.0);
        if (a instanceof NpcEntity ne) {
            if (fast) {
                teleportNpc(ne, safeSpot(to));
                return null;
            }
            return new MoveTask(ne, to, speed, within);
        }
        if (a == player) {
            playerMoved = true;
            if (fast) {
                teleportPlayer(to, player.getYRot(), player.getXRot());
                return null;
            }
            return new PlayerMoveTask(to, speed, within);
        }
        return null;
    }

    private Task execAnimate(JsonObject o) {
        Entity a = actor(o);
        switch (Json.str(o, "anim", "swing")) {
            case "swing" -> { if (!fast && a instanceof LivingEntity le) le.swing(InteractionHand.MAIN_HAND); }
            case "hurt" -> { if (!fast) level.broadcastEntityEvent(a, (byte) 2); }
            case "jump" -> { if (!fast && a instanceof Mob m) m.getJumpControl().jump(); }
            case "crouch" -> {
                if (fast) return null;
                a.setPose(Pose.CROUCHING);
                final int[] left = {ticks(o, 30)};
                return new Task() {
                    public boolean tick() {
                        if (--left[0] <= 0) {
                            a.setPose(Pose.STANDING);
                            return true;
                        }
                        return false;
                    }
                    public void complete() { a.setPose(Pose.STANDING); }
                };
            }
            default -> throw new IllegalArgumentException("unknown animation");
        }
        return null;
    }

    // ------------------------------------------------------------------ tasks

    private static final class WaitTask implements Task {
        private int left;
        WaitTask(int ticks) { left = ticks; }
        public boolean tick() { return --left <= 0; }
    }

    private final class TalkTask implements Task {
        private final Cutscene.Step step;
        TalkTask(Cutscene.Step step) { this.step = step; }

        public boolean tick() {
            if (DialogueManager.isActive(player.getUUID(), npc)) return false;
            complete();
            return true;
        }

        public void complete() { applyBranch(step); }
    }

    private final class MoveTask implements Task {
        private final NpcEntity e;
        private final Vec3 to;
        private final double speed, within;
        private final int limit;
        private int ticks, repaths;

        MoveTask(NpcEntity e, Vec3 to, double speed, double within) {
            this.e = e;
            this.to = to;
            this.speed = speed;
            this.within = within;
            this.limit = (int) Math.max(60, e.position().distanceTo(to) * 30);
            e.getNavigation().moveTo(to.x, to.y, to.z, speed);
        }

        public boolean tick() {
            ticks++;
            double dx = e.getX() - to.x, dz = e.getZ() - to.z;
            if (Math.sqrt(dx * dx + dz * dz) <= within && Math.abs(e.getY() - to.y) < 2.5) {
                e.getNavigation().stop();
                return true;
            }
            if (e.getNavigation().isDone() && ticks % 10 == 0) {
                if (++repaths > 4 || !e.getNavigation().moveTo(to.x, to.y, to.z, speed)) {
                    complete();
                    return true;
                }
            }
            if (ticks > limit) {
                complete();
                return true;
            }
            return false;
        }

        public void complete() { teleportNpc(e, safeSpot(to)); }
    }

    /** Straight-line walk for the player (the server drives position; client input is locked). */
    private final class PlayerMoveTask implements Task {
        private final Vec3 to;
        private final double step, within;

        PlayerMoveTask(Vec3 to, double speed, double within) {
            this.to = to;
            this.step = 0.215 * speed;
            this.within = within;
        }

        public boolean tick() {
            Vec3 cur = player.position();
            Vec3 d = to.subtract(cur);
            double dist = d.length();
            if (dist <= Math.max(step, within)) return true;
            Vec3 n = cur.add(d.scale(step / dist));
            teleportPlayer(n, yawOf(d), 0f);
            return false;
        }

        public void complete() { teleportPlayer(to, player.getYRot(), player.getXRot()); }
    }

    // ------------------------------------------------------------------ actors & positions

    private Entity actor(JsonObject o) {
        String name = Json.str(o, "actor", "npc");
        Entity e = actors.get(name);
        if (e == null) throw new IllegalArgumentException("unknown actor '" + name + "' (spawn it first or list it under \"actors\")");
        return e;
    }

    private void control(NpcEntity e, boolean restore) {
        e.setCutsceneControlled(true);
        e.getNavigation().stop();
        controlled.add(e);
        if (restore) restorePos.put(e, e.position());
    }

    /** Uses the real NPC if present and free; otherwise spawns a look-alike stand-in so the scene still plays. */
    private Entity findActor(String npcId) {
        if (npcId.equals(def.id)) return npc;
        Village v = npc.village();
        if (v != null) {
            Resident r = v.residents.get(npcId);
            if (r != null && r.settled && level.getEntity(r.entity) instanceof NpcEntity ne
                    && !ne.isCutsceneControlled() && !ne.isTalking() && ne.distanceToSqr(npc) < 64 * 64) {
                control(ne, true);
                return ne;
            }
        }
        NpcDefinition d = NpcRegistry.get(npcId);
        return spawnTemp(npcId, d == null ? Ids.path(npcId) : d.name, safeSpot(origin));
    }

    private NpcEntity spawnTemp(String skinId, String name, Vec3 at) {
        NpcEntity e = ModRegistry.NPC.get().create(level);
        if (e == null) throw new IllegalStateException("could not create actor");
        e.moveTo(at.x, at.y, at.z, 0f, 0f);
        e.setTemp(skinId, name);
        e.setCutsceneControlled(true);
        level.addFreshEntity(e);
        extras.add(e);
        controlled.add(e);
        return e;
    }

    private Vec3 resolveOrigin() {
        JsonElement o = cs.origin;
        if (o == null) return npc.position();
        if (o.isJsonArray()) return vec(o.getAsJsonArray());
        Village v = npc.village();
        switch (o.getAsString()) {
            case "player": return player.position();
            case "hearth": return v != null ? Vec3.atBottomCenterOf(v.hearth.above()) : npc.position();
            case "building": {
                Building b = v == null ? null : v.buildingAt(npc.blockPosition());
                return b == null ? npc.position() : Vec3.atBottomCenterOf(b.spotFor(def.id));
            }
            default: return npc.position();
        }
    }

    /** array = offset from origin | "name" = mark or actor position | {"from":..,"offset":[..]} | {"abs":[x,y,z]} */
    private Vec3 pos(JsonElement e) {
        if (e == null || e.isJsonNull()) throw new IllegalArgumentException("missing position");
        if (e.isJsonArray()) return origin.add(vec(e.getAsJsonArray()));
        if (e.isJsonPrimitive()) {
            String s = e.getAsString();
            JsonElement m = cs.marks.get(s);
            if (m != null) return origin.add(vec(m.getAsJsonArray()));
            Entity a = actors.get(s);
            if (a != null) return a.position();
            throw new IllegalArgumentException("unknown position/actor '" + s + "'");
        }
        JsonObject o = e.getAsJsonObject();
        if (o.has("abs")) return vec(o.getAsJsonArray("abs"));
        Vec3 base = pos(o.get("from"));
        return o.has("offset") ? base.add(vec(o.getAsJsonArray("offset"))) : base;
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    /** Nearest standable spot (2 free blocks above solid ground) so spawned/warped NPCs never end up inside walls. */
    private Vec3 safeSpot(Vec3 v) {
        BlockPos base = BlockPos.containing(v);
        int[] dys = {0, 1, -1, 2, -2};
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : dys) {
                        BlockPos p = base.offset(dx, dy, dz);
                        if (free(p)) return Vec3.atBottomCenterOf(p);
                    }
                }
            }
        }
        return v;
    }

    private boolean free(BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    // ------------------------------------------------------------------ helpers

    private int ticks(JsonObject o, int def) {
        if (o.has("seconds")) return (int) (Json.dbl(o, "seconds", 1) * 20);
        return Json.integer(o, "ticks", def);
    }

    private static float yawOf(Vec3 d) {
        return (float) (Mth.atan2(-d.x, d.z) * (180.0 / Math.PI));
    }

    private void faceToward(Entity a, Vec3 target) {
        Vec3 d = target.subtract(a.position());
        setFacing(a, yawOf(d), 0f);
    }

    private void setFacing(Entity a, float yaw, float pitch) {
        if (a == player) {
            teleportPlayer(player.position(), yaw, pitch);
        } else if (a instanceof LivingEntity le) {
            le.setYRot(yaw);
            le.yRotO = yaw;
            le.setYHeadRot(yaw);
            le.yHeadRotO = yaw;
            le.setYBodyRot(yaw);
            le.yBodyRotO = yaw;
            le.setXRot(pitch);
        }
    }

    private void teleportNpc(NpcEntity e, Vec3 v) {
        e.getNavigation().stop();
        e.moveTo(v.x, v.y, v.z, e.getYRot(), 0f);
    }

    private void teleportPlayer(Vec3 v, float yaw, float pitch) {
        if (!player.hasDisconnected()) player.connection.teleport(v.x, v.y, v.z, yaw, pitch);
    }

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (!player.hasDisconnected()) PacketDistributor.sendToPlayer(player, payload);
    }

    private static ParticleOptions emoteParticle(String e) {
        return switch (e) {
            case "happy" -> ParticleTypes.HAPPY_VILLAGER;
            case "angry" -> ParticleTypes.ANGRY_VILLAGER;
            case "music" -> ParticleTypes.NOTE;
            case "sad" -> ParticleTypes.SMOKE;
            case "exclaim" -> ParticleTypes.CRIT;
            case "question" -> ParticleTypes.ENCHANT;
            case "sweat" -> ParticleTypes.SPLASH;
            default -> ParticleTypes.HEART;
        };
    }
}
