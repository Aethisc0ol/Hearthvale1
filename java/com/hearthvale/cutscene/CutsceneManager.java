package com.hearthvale.cutscene;

import com.hearthvale.Hearthvale;
import com.hearthvale.data.NpcDefinition;
import com.hearthvale.dialogue.DialogueManager;
import com.hearthvale.dialogue.DialogueSession;
import com.hearthvale.entity.NpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One running cutscene per player. */
public final class CutsceneManager {
    private static final Map<UUID, CutsceneRunner> RUNNERS = new HashMap<>();

    private CutsceneManager() {}

    public static boolean start(ServerPlayer player, NpcEntity npc, NpcDefinition def, Cutscene cs) {
        if (RUNNERS.containsKey(player.getUUID()) || npc.isCutsceneControlled()) return false;
        if (player.isPassenger()) player.stopRiding();
        CutsceneRunner r = new CutsceneRunner(player, npc, def, cs);
        RUNNERS.put(player.getUUID(), r);
        try {
            r.begin();
        } catch (Exception e) {
            Hearthvale.LOGGER.error("Cutscene failed to start", e);
            r.abort();
            return false;
        }
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (RUNNERS.isEmpty()) return;
        for (CutsceneRunner r : new ArrayList<>(RUNNERS.values())) {
            try {
                r.tick();
            } catch (Exception e) {
                Hearthvale.LOGGER.error("Cutscene crashed, aborting it", e);
                r.abort();
            }
        }
    }

    public static boolean isRunning(UUID player) { return RUNNERS.containsKey(player); }

    public static boolean isActor(NpcEntity e) {
        for (CutsceneRunner r : RUNNERS.values()) if (r.extras.contains(e)) return true;
        return false;
    }

    /** ESC pressed during a cutscene. */
    public static void skip(ServerPlayer player) {
        CutsceneRunner r = RUNNERS.get(player.getUUID());
        if (r == null) return;
        if (!r.cs.skippable) {
            player.displayClientMessage(Component.literal("This scene can't be skipped."), true);
            DialogueSession ds = DialogueManager.get(player.getUUID());
            if (ds != null) ds.resend();
            return;
        }
        r.skip();
    }

    public static void abort(UUID player) {
        CutsceneRunner r = RUNNERS.get(player);
        if (r != null) r.abort();
    }

    public static void chooseBranch(UUID player, int index) {
        CutsceneRunner r = RUNNERS.get(player);
        if (r != null) r.pendingBranch = index;
    }

    static void remove(CutsceneRunner r) { RUNNERS.remove(r.player.getUUID(), r); }

    public static void clear() { RUNNERS.clear(); }
}
