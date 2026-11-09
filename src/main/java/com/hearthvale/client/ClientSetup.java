package com.hearthvale.client;

import com.hearthvale.Hearthvale;
import com.hearthvale.ModRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import com.hearthvale.client.book.BookKeys;
import net.minecraft.resources.ResourceLocation;

@EventBusSubscriber(modid = Hearthvale.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(BookKeys.OPEN);
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "cutscene"), CutsceneOverlay::render);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.NPC.get(), NpcRenderer::new);
    }
}
