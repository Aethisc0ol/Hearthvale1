package com.hearthvale.client;

import com.hearthvale.Hearthvale;
import com.hearthvale.client.book.BookKeys;
import com.hearthvale.network.CutsceneSkipPayload;
import com.hearthvale.network.RequestBookPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Locks player control and hides the vanilla HUD while a cutscene is running. */
@EventBusSubscriber(modid = Hearthvale.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        ClientCutscene.tick();
        Minecraft mc = Minecraft.getInstance();
        while (BookKeys.OPEN.consumeClick()) {
            if (mc.screen == null && mc.player != null && !ClientCutscene.active) PacketDistributor.sendToServer(new RequestBookPayload(true));
        }
    }

    @SubscribeEvent
    public static void move(MovementInputUpdateEvent e) {
        if (!ClientCutscene.active) return;
        Input in = e.getInput();
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.forwardImpulse = 0f;
        in.leftImpulse = 0f;
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (!ClientCutscene.active) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (ClientCutscene.active && Minecraft.getInstance().screen == null) e.setCanceled(true);
    }

    /** ESC would open the pause menu - during a cutscene it means "skip". */
    @SubscribeEvent
    public static void screen(ScreenEvent.Opening e) {
        if (!ClientCutscene.active) return;
        if (e.getNewScreen() instanceof PauseScreen) {
            e.setCanceled(true);
            if (ClientCutscene.skippable) PacketDistributor.sendToServer(new CutsceneSkipPayload());
        } else if (e.getNewScreen() instanceof AbstractContainerScreen<?>) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hud(RenderGuiLayerEvent.Pre e) {
        if (ClientCutscene.active && !e.getName().getNamespace().equals(Hearthvale.MODID)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientCutscene.reset();
    }
}
