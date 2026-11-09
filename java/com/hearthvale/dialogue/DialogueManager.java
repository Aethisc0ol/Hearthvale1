package com.hearthvale.dialogue;

import com.hearthvale.cutscene.CutsceneManager;
import com.hearthvale.data.Script;
import com.hearthvale.entity.NpcEntity;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks the one active conversation per player (server side). */
public final class DialogueManager {
    private static final Map<UUID, DialogueSession> SESSIONS = new HashMap<>();

    private DialogueManager() {}

    public static void start(ServerPlayer player, NpcEntity npc, Script script, Runnable onFinish) {
        end(player.getUUID());
        DialogueSession s = new DialogueSession(player, npc, script, onFinish);
        SESSIONS.put(player.getUUID(), s);
        npc.setTalkingTo(player.getUUID());
        s.step();
    }

    public static void respond(ServerPlayer player, int choice) {
        DialogueSession s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        if (s.npc.isRemoved() || (!CutsceneManager.isRunning(player.getUUID()) && s.npc.distanceToSqr(player) > 400)) {
            s.cancel();
            return;
        }
        s.respond(choice);
    }

    public static boolean has(UUID player) { return SESSIONS.containsKey(player); }

    public static DialogueSession get(UUID player) { return SESSIONS.get(player); }

    public static boolean isActive(UUID player, NpcEntity npc) {
        DialogueSession s = SESSIONS.get(player);
        return s != null && s.npc == npc;
    }

    public static boolean isBusyWith(NpcEntity npc, ServerPlayer asker) {
        return npc.isTalking() && !asker.getUUID().equals(npc.getTalkingTo()) && isActive(npc.getTalkingTo(), npc);
    }

    /** Cancel a player's conversation (no completion callback). */
    public static void end(UUID player) {
        DialogueSession s = SESSIONS.get(player);
        if (s != null) s.cancel();
    }

    static void remove(DialogueSession s) {
        SESSIONS.remove(s.player.getUUID(), s);
    }

    public static void clear() { SESSIONS.clear(); }
}
