package com.hearthvale;

import com.hearthvale.command.HearthvaleCommands;
import com.hearthvale.data.BuildingRegistry;
import com.hearthvale.data.NpcRegistry;
import com.hearthvale.cutscene.CutsceneManager;
import com.hearthvale.dialogue.DialogueManager;
import com.hearthvale.world.VillageManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus events. */
@EventBusSubscriber(modid = Hearthvale.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent e) {
        e.addListener(new BuildingRegistry());
        e.addListener(new NpcRegistry());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        VillageManager.tick(e.getServer());
        CutsceneManager.tick(e.getServer());
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        HearthvaleCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        CutsceneManager.abort(e.getEntity().getUUID());
        DialogueManager.end(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        CutsceneManager.abort(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) CutsceneManager.abort(p.getUUID());
    }

    /** Players can't be hurt while a scene is playing. */
    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && CutsceneManager.isRunning(p.getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        CutsceneManager.clear();
        DialogueManager.clear();
    }
}
