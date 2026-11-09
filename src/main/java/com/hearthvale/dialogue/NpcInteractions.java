package com.hearthvale.dialogue;

import com.hearthvale.ModRegistry;
import com.hearthvale.cutscene.CutsceneManager;
import com.hearthvale.data.*;
import com.hearthvale.entity.NpcEntity;
import com.hearthvale.util.Fonts;
import com.hearthvale.util.GameTime;
import com.hearthvale.world.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * All the Stardew-style rules live here: daily talk points, gifts (loved/liked/neutral/disliked/hated, birthdays,
 * weekly limits), bouquet -> dating at 8 hearts, pendant -> engaged at 10 hearts, wedding 3 days later, hugs, events.
 *
 * Controls: right-click = talk | sneak + right-click holding an item = gift | sneak + right-click empty hand = hug/kiss
 * | right-click holding a Bouquet / Promise Pendant = romance.
 */
public final class NpcInteractions {
    private static final Random R = new Random();

    private NpcInteractions() {}

    private static void bar(ServerPlayer p, String text) {
        p.displayClientMessage(Fonts.text(text), true);
    }

    // ------------------------------------------------------------------ main entry

    public static void interact(ServerPlayer player, NpcEntity npc, ItemStack held, boolean sneaking) {
        ServerLevel level = player.serverLevel();
        NpcDefinition def = npc.def();
        if (def == null) {
            bar(player, "This villager's data is missing (was an addon removed?).");
            return;
        }
        if (npc.isCutsceneControlled() || DialogueManager.isBusyWith(npc, player)) {
            bar(player, def.name + " is busy talking to someone else.");
            return;
        }
        WorldState ws = WorldState.get(level);
        PlayerData pd = ws.player(player.getUUID());
        Relationship rel = pd.rel(def.id);

        if (npc.getNpcState() != NpcEntity.STATE_RESIDENT) {
            Script s = def.arrival != null ? def.arrival : DefaultLines.arrival(def);
            DialogueManager.start(player, npc, s, () -> VillageManager.settle(npc, player));
        } else if (held.is(ModRegistry.BOUQUET.get())) {
            bouquet(player, npc, def, rel, held);
        } else if (held.is(ModRegistry.PENDANT.get())) {
            pendant(player, npc, def, pd, rel, held);
        } else if (sneaking && !held.isEmpty()) {
            gift(player, npc, def, rel, held);
        } else if (sneaking) {
            affection(player, npc, def, rel);
        } else {
            talk(player, npc, def, pd, rel);
        }
        ws.setDirty();
    }

    // ------------------------------------------------------------------ talking & events

    private static void talk(ServerPlayer player, NpcEntity npc, NpcDefinition def, PlayerData pd, Relationship rel) {
        ServerLevel level = player.serverLevel();
        long day = GameTime.day(level);
        if (weddingDue(rel, day)) {
            startWedding(player, npc, def);
            return;
        }
        if (rel.talkedDay != day) {
            rel.talkedDay = day;
            addPoints(player, npc, def, rel, 20);
        }
        Conditions.Ctx ctx = ctx(level, npc, def, pd, rel);
        for (NpcDefinition.NpcEvent ev : def.events) {
            if (!rel.events.contains(ev.id()) && ev.cond().test(ctx)) {
                if (startEvent(player, npc, rel, ev)) return;
            }
        }
        DialogueManager.start(player, npc, new Script(List.of(pickGreeting(def, rel, ctx))), null);
    }

    private static Script.Line pickGreeting(NpcDefinition def, Relationship rel, Conditions.Ctx ctx) {
        List<NpcDefinition.Greeting> matches = new ArrayList<>();
        int best = -1;
        for (NpcDefinition.Greeting g : def.greetings) {
            if (!g.cond().test(ctx)) continue;
            int s = g.cond().specificity();
            if (s > best) {
                best = s;
                matches.clear();
            }
            if (s == best) matches.add(g);
        }
        if (matches.isEmpty()) return DefaultLines.greeting(rel);
        return matches.get(R.nextInt(matches.size())).line();
    }

    /** Plays a dialogue-only event or a full cutscene. Returns false (event stays unseen) if it couldn't start. */
    private static boolean startEvent(ServerPlayer player, NpcEntity npc, Relationship rel, NpcDefinition.NpcEvent ev) {
        NpcDefinition def = npc.def();
        if (ev.cutscene() != null) {
            if (def == null || !CutsceneManager.start(player, npc, def, ev.cutscene())) return false;
        } else {
            DialogueManager.start(player, npc, ev.script(), null);
        }
        rel.events.add(ev.id());
        return true;
    }

    /** Called by the NPC every second: heart events and weddings trigger when you walk up to them. */
    public static void checkApproach(NpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        NpcDefinition def = npc.def();
        if (def == null) return;
        Player near = level.getNearestPlayer(npc, 5.0);
        if (!(near instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator() || player.isSleeping()) return;
        if (DialogueManager.has(player.getUUID())) return;
        int hurt = player.getLastHurtByMobTimestamp();
        if (hurt != 0 && player.tickCount - hurt < 200) return; // don't interrupt fights

        WorldState ws = WorldState.get(level);
        PlayerData pd = ws.player(player.getUUID());
        Relationship rel = pd.peek(def.id);
        if (weddingDue(rel, GameTime.day(level))) {
            startWedding(player, npc, def);
            ws.setDirty();
            return;
        }
        Conditions.Ctx ctx = ctx(level, npc, def, pd, rel);
        for (NpcDefinition.NpcEvent ev : def.events) {
            if (ev.approach() && !rel.events.contains(ev.id()) && ev.cond().test(ctx)) {
                if (startEvent(player, npc, pd.rel(def.id), ev)) {
                    ws.setDirty();
                    return;
                }
            }
        }
    }

    /** Admin/testing: play an event regardless of its conditions. */
    public static boolean forceEvent(ServerPlayer player, NpcEntity npc, String eventId) {
        NpcDefinition def = npc.def();
        if (def == null) return false;
        for (NpcDefinition.NpcEvent ev : def.events) {
            if (ev.id().equals(eventId)) {
                Relationship rel = WorldState.get(player.serverLevel()).player(player.getUUID()).rel(def.id);
                return startEvent(player, npc, rel, ev);
            }
        }
        return false;
    }

    public static Conditions.Ctx ctx(ServerLevel level, NpcEntity npc, NpcDefinition def, PlayerData pd, Relationship rel) {
        String loc = null;
        Village v = npc.village();
        if (v != null) {
            Building b = v.buildingAt(npc.blockPosition());
            if (b != null) loc = b.type;
        }
        return new Conditions.Ctx(level, def, pd, rel, loc);
    }

    // ------------------------------------------------------------------ friendship

    public static void addPoints(ServerPlayer player, NpcEntity npc, NpcDefinition def, Relationship rel, int delta) {
        int before = rel.hearts();
        int applied = rel.add(delta, def.romanceable);
        ServerLevel level = player.serverLevel();
        if (applied > 0) {
            level.sendParticles(ParticleTypes.HEART, npc.getX(), npc.getY() + 2.1, npc.getZ(), Math.min(6, 1 + applied / 20), 0.3, 0.2, 0.3, 0.02);
        } else if (applied < 0) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, npc.getX(), npc.getY() + 2.1, npc.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
        }
        if (rel.hearts() != before) bar(player, "\u00a7c\u2665 \u00a7f" + def.name + " \u00a77- \u00a7c" + rel.hearts() + " hearts");
    }

    private static void gift(ServerPlayer player, NpcEntity npc, NpcDefinition def, Relationship rel, ItemStack held) {
        ServerLevel level = player.serverLevel();
        long day = GameTime.day(level);
        long week = day / 7;
        if (rel.giftedDay == day) {
            bar(player, "You already gave " + def.name + " a gift today.");
            return;
        }
        if (rel.giftWeek != week) {
            rel.giftWeek = week;
            rel.giftsThisWeek = 0;
        }
        if (rel.giftsThisWeek >= 2) {
            bar(player, "You've already given " + def.name + " two gifts this week.");
            return;
        }
        String cat = def.giftCategory(held);
        int pts = switch (cat) {
            case "loved" -> 80;
            case "liked" -> 45;
            case "disliked" -> -20;
            case "hated" -> -40;
            default -> 20;
        };
        if (def.isBirthday(level) && (cat.equals("loved") || cat.equals("liked"))) pts *= 8;

        String itemName = held.getHoverName().getString();
        rel.giftedDay = day;
        rel.giftsThisWeek++;
        if (!player.getAbilities().instabuild) held.shrink(1);

        List<String> lines = def.reactions.get(cat);
        String line = lines != null && !lines.isEmpty() ? lines.get(R.nextInt(lines.size())) : DefaultLines.reaction(cat);
        DialogueManager.start(player, npc, Script.of(line.replace("{item}", itemName)), null);
        addPoints(player, npc, def, rel, pts);
    }

    private static void affection(ServerPlayer player, NpcEntity npc, NpcDefinition def, Relationship rel) {
        if (rel.status == Relationship.Status.FRIEND) {
            bar(player, def.name + " gives you a polite, slightly confused look.");
            return;
        }
        long day = GameTime.day(player.serverLevel());
        if (rel.affectionDay == day) {
            bar(player, def.name + " smiles - you've already shared a moment today.");
            return;
        }
        rel.affectionDay = day;
        DialogueManager.start(player, npc, DefaultLines.affection(), null);
        addPoints(player, npc, def, rel, 10);
    }

    // ------------------------------------------------------------------ romance

    private static void bouquet(ServerPlayer player, NpcEntity npc, NpcDefinition def, Relationship rel, ItemStack held) {
        if (!def.romanceable) {
            DialogueManager.start(player, npc, def.bouquetReject != null ? def.bouquetReject : DefaultLines.notRomanceable(), null);
            return;
        }
        if (rel.status != Relationship.Status.FRIEND) {
            bar(player, def.name + " already knows how you feel.");
            return;
        }
        if (rel.hearts() < 8) {
            DialogueManager.start(player, npc, def.bouquetReject != null ? def.bouquetReject : DefaultLines.tooEarly(), null);
            return;
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        rel.status = Relationship.Status.DATING;
        DialogueManager.start(player, npc, def.bouquetAccept != null ? def.bouquetAccept : DefaultLines.bouquetAccept(), null);
        player.serverLevel().sendParticles(ParticleTypes.HEART, npc.getX(), npc.getY() + 2.0, npc.getZ(), 12, 0.5, 0.4, 0.5, 0.05);
        player.sendSystemMessage(Component.literal("\u00a7d\u2665 You are now dating " + def.name + "! Reach 10 hearts and offer a Promise Pendant to propose."));
    }

    private static void pendant(ServerPlayer player, NpcEntity npc, NpcDefinition def, PlayerData pd, Relationship rel, ItemStack held) {
        if (rel.status == Relationship.Status.MARRIED || rel.status == Relationship.Status.ENGAGED) {
            bar(player, def.name + " already wears your promise.");
            return;
        }
        String partner = pd.partner();
        if (partner != null && !partner.equals(def.id)) {
            bar(player, "You are already promised to someone else.");
            return;
        }
        if (!def.romanceable || rel.status != Relationship.Status.DATING || rel.hearts() < 10) {
            DialogueManager.start(player, npc, def.pendantReject != null ? def.pendantReject : DefaultLines.pendantReject(), null);
            return;
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        rel.status = Relationship.Status.ENGAGED;
        rel.weddingDay = GameTime.day(player.serverLevel()) + 3;
        DialogueManager.start(player, npc, def.pendantAccept != null ? def.pendantAccept : DefaultLines.pendantAccept(), null);
        player.sendSystemMessage(Component.literal("\u00a7d\u2665 " + def.name + " said yes! The wedding is in 3 days - find them near the hearth."));
    }

    private static boolean weddingDue(Relationship rel, long day) {
        return rel.status == Relationship.Status.ENGAGED && rel.weddingDay >= 0 && day >= rel.weddingDay;
    }

    private static void startWedding(ServerPlayer player, NpcEntity npc, NpcDefinition def) {
        Script s = def.wedding != null ? def.wedding : DefaultLines.wedding();
        DialogueManager.start(player, npc, s, () -> marry(player, npc, def));
    }

    public static void marry(ServerPlayer player, NpcEntity npc, NpcDefinition def) {
        ServerLevel level = player.serverLevel();
        WorldState ws = WorldState.get(level);
        Relationship rel = ws.player(player.getUUID()).rel(def.id);
        if (rel.status == Relationship.Status.MARRIED) return;
        rel.status = Relationship.Status.MARRIED;
        rel.weddingDay = -1;
        VillageManager.moveInWithPlayer(npc, player);
        level.sendParticles(ParticleTypes.HEART, npc.getX(), npc.getY() + 2.0, npc.getZ(), 30, 1.0, 0.8, 1.0, 0.1);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("\u00a7d\u2665 \u00a7f" + player.getGameProfile().getName() + " \u00a7dand \u00a7f" + def.name + " \u00a7dare now married!"), false);
        ws.setDirty();
    }

    // ------------------------------------------------------------------ journal

    public static void journal(ServerPlayer player) {
        WorldState ws = WorldState.get(player.serverLevel());
        PlayerData pd = ws.player(player.getUUID());
        player.sendSystemMessage(Component.literal("\u00a76\u00a7l=== Village Journal ==="));
        int shown = 0;
        for (Map.Entry<String, Relationship> e : pd.relationships.entrySet()) {
            NpcDefinition d = NpcRegistry.get(e.getKey());
            if (d == null) continue;
            Relationship r = e.getValue();
            Village v = ws.villageOfNpc(d.id);
            int h = Math.min(r.hearts(), 14);
            int total = Math.max(10, h);
            String bar = "\u00a7c" + "\u2665".repeat(h) + "\u00a78" + "\u2665".repeat(total - h);
            player.sendSystemMessage(Component.literal("\u00a7e" + d.name + " " + bar + " \u00a7f" + r.status.label()
                    + " \u00a78| \u00a77Birthday: " + d.birthdayLabel() + " \u00a78| \u00a77" + (v == null ? "not in a village" : v.name)));
            shown++;
        }
        if (shown == 0) player.sendSystemMessage(Component.literal("\u00a77You haven't met anyone yet. Found a village and build some houses!"));
        player.sendSystemMessage(Component.literal("\u00a78Tip: sneak + right-click with an item to gift it; sneak + empty hand to hug someone you're close to."));
    }
}
