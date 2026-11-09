package com.hearthvale;

import com.hearthvale.book.BookDataBuilder;
import com.hearthvale.client.ClientPayloadHandler;
import com.hearthvale.dialogue.DialogueManager;
import com.hearthvale.entity.NpcEntity;
import com.hearthvale.cutscene.CutsceneManager;
import com.hearthvale.network.*;
import com.hearthvale.network.DialoguePagePayload;
import com.hearthvale.network.DialogueResponsePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Mod-bus events: attributes, creative tabs, network registration. */
@EventBusSubscriber(modid = Hearthvale.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(ModRegistry.NPC.get(), NpcEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            e.accept(ModRegistry.HEARTH_ITEM.get());
        }
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(ModRegistry.DEED.get());
            e.accept(ModRegistry.JOURNAL.get());
            e.accept(ModRegistry.BOUQUET.get());
            e.accept(ModRegistry.PENDANT.get());
        }
    }

    @SubscribeEvent
    public static void payloads(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToClient(DialoguePagePayload.TYPE, DialoguePagePayload.CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.handlePage(payload);
        });
        r.playToClient(CutsceneStatePayload.TYPE, CutsceneStatePayload.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.handleState(p);
        });
        r.playToClient(CameraPayload.TYPE, CameraPayload.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.handleCamera(p);
        });
        r.playToClient(FadePayload.TYPE, FadePayload.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.handleFade(p);
        });
        r.playToClient(TitlePayload.TYPE, TitlePayload.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.handleTitle(p);
        });
        r.playToClient(BookDataPayload.TYPE, BookDataPayload.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPayloadHandler.handleBook(p);
        });
        r.playToServer(RequestBookPayload.TYPE, RequestBookPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new BookDataPayload(p.open(), BookDataBuilder.build(sp)));
        });
        r.playToServer(SettingsPayload.TYPE, SettingsPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                BookDataBuilder.applySetting(sp, p.key(), p.value());
                PacketDistributor.sendToPlayer(sp, new BookDataPayload(false, BookDataBuilder.build(sp)));
            }
        });
        r.playToServer(CutsceneSkipPayload.TYPE, CutsceneSkipPayload.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) CutsceneManager.skip(sp);
        });
        r.playToServer(DialogueResponsePayload.TYPE, DialogueResponsePayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) DialogueManager.respond(sp, payload.choice());
        });
    }
}
