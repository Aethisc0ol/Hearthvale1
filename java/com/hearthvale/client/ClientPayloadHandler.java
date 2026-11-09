package com.hearthvale.client;

import com.hearthvale.client.book.BookScreen;
import com.hearthvale.network.BookDataPayload;
import com.hearthvale.network.CameraPayload;
import com.hearthvale.network.CutsceneStatePayload;
import com.hearthvale.network.DialoguePagePayload;
import com.hearthvale.network.FadePayload;
import com.hearthvale.network.TitlePayload;
import net.minecraft.client.Minecraft;

/** Only ever touched on the client (referenced lazily from the payload handler). */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void handleBook(BookDataPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BookScreen bs) bs.update(p.data());
        else if (p.open()) BookScreen.open(p.data());
    }

    public static void handleState(CutsceneStatePayload p) { ClientCutscene.state(p); }
    public static void handleCamera(CameraPayload p) { ClientCutscene.camera(p); }
    public static void handleFade(FadePayload p) { ClientCutscene.fade(p); }
    public static void handleTitle(TitlePayload p) { ClientCutscene.title(p); }

    public static void handlePage(DialoguePagePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.text().isEmpty()) {
            if (mc.screen instanceof DialogueScreen) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof DialogueScreen ds && ds.entityId == p.entityId()) ds.setPage(p);
        else mc.setScreen(new DialogueScreen(p));
    }
}
