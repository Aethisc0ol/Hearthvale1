package com.hearthvale.client;

import com.hearthvale.network.CameraPayload;
import com.hearthvale.network.CutsceneStatePayload;
import com.hearthvale.network.FadePayload;
import com.hearthvale.network.TitlePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Client half of the cutscene system (no extra mods needed):
 *  - a dummy camera entity (never added to the world) that we move every client tick; the renderer interpolates it per frame
 *  - letterbox / fade / title state drawn by {@link CutsceneOverlay}
 *  - the "input locked" flag read by {@link ClientEvents}
 */
public final class ClientCutscene {
    public static boolean active, skippable, letterbox;
    public static int ticksActive;

    // overlay state
    public static float barProgress, barProgressO;
    public static int fadeColor = 0x000000;
    public static float fadeAlpha, fadeAlphaO, fadeFrom, fadeTo;
    private static int fadeTicks, fadeElapsed;
    public static String title = "";
    public static int titleTicks, titleAge;

    // camera state
    private static ArmorStand dummy;
    private static boolean camActive;
    private static Vec3 camPos = Vec3.ZERO, from = Vec3.ZERO, to = Vec3.ZERO, lookPoint = Vec3.ZERO;
    private static float camYaw, camPitch, yawFrom, pitchFrom, yawTo, pitchTo;
    private static int lookType, lookEntity = -1, dur, elapsed;
    private static boolean smooth = true;

    private ClientCutscene() {}

    // ------------------------------------------------------------ packets

    public static void state(CutsceneStatePayload p) {
        if (!p.active()) {
            reset();
            return;
        }
        if (!active) {
            ticksActive = 0;
            barProgress = barProgressO = 0;
            fadeAlpha = fadeAlphaO = fadeFrom = fadeTo = 0;
            title = "";
        }
        active = true;
        skippable = p.skippable();
        letterbox = p.letterbox();
    }

    public static void fade(FadePayload p) {
        if (p.color() >= 0) fadeColor = p.color();
        fadeFrom = fadeAlpha;
        fadeTo = p.alpha();
        fadeTicks = Math.max(0, p.ticks());
        fadeElapsed = 0;
        if (fadeTicks == 0) fadeAlpha = fadeAlphaO = fadeTo;
    }

    public static void title(TitlePayload p) {
        title = p.text();
        titleTicks = Math.max(1, p.ticks());
        titleAge = 0;
    }

    public static void camera(CameraPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (p.mode() == 1) {
            releaseCamera();
            return;
        }
        if (!camActive) startCamera(mc);
        from = camPos;
        yawFrom = camYaw;
        pitchFrom = camPitch;
        to = p.hasPos() ? new Vec3(p.x(), p.y(), p.z()) : camPos;
        if (p.lookType() != 0) {
            lookType = p.lookType();
            lookEntity = p.entityId();
            lookPoint = new Vec3(p.lx(), p.ly(), p.lz());
            yawTo = p.yaw();
            pitchTo = p.pitch();
        }
        dur = p.ticks();
        elapsed = 0;
        smooth = p.smooth();
        if (dur <= 0) camStep(true);
    }

    // ------------------------------------------------------------ ticking

    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        ticksActive++;
        barProgressO = barProgress;
        barProgress = Mth.clamp(barProgress + (letterbox ? 0.1f : -0.1f), 0f, 1f);

        fadeAlphaO = fadeAlpha;
        if (fadeElapsed < fadeTicks) {
            fadeElapsed++;
            fadeAlpha = Mth.lerp(fadeElapsed / (float) fadeTicks, fadeFrom, fadeTo);
        } else {
            fadeAlpha = fadeTo;
        }
        if (!title.isEmpty() && ++titleAge > titleTicks) title = "";

        if (camActive) camStep(false);
        if (mc.screen == null && mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
    }

    private static void startCamera(Minecraft mc) {
        dummy = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        camPos = mc.player.getEyePosition();
        camYaw = mc.player.getYRot();
        camPitch = mc.player.getXRot();
        lookType = 0;
        lookEntity = -1;
        apply(camPos, camYaw, camPitch, true);
        mc.setCameraEntity(dummy);
        camActive = true;
    }

    private static void camStep(boolean snap) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || dummy == null) return;
        float t;
        if (dur <= 0) {
            t = 1f;
        } else {
            if (elapsed < dur) elapsed++;
            t = Math.min(1f, elapsed / (float) dur);
        }
        float e = smooth ? t * t * (3f - 2f * t) : t;
        Vec3 pos = from.lerp(to, e);

        float yawD = camYaw, pitchD = camPitch;
        switch (lookType) {
            case 1 -> {
                yawD = Mth.rotLerp(e, yawFrom, yawTo);
                pitchD = Mth.lerp(e, pitchFrom, pitchTo);
            }
            case 2, 3 -> {
                Vec3 target = lookPoint;
                if (lookType == 3) {
                    Entity en = mc.level.getEntity(lookEntity);
                    if (en != null) target = en.getEyePosition().subtract(0, 0.15, 0);
                }
                double dx = target.x - pos.x, dy = target.y - pos.y, dz = target.z - pos.z;
                float yawA = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
                float pitchA = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
                yawD = Mth.rotLerp(e, yawFrom, yawA);
                pitchD = Mth.lerp(e, pitchFrom, pitchA);
            }
            default -> { }
        }
        // keep yaw continuous so the renderer's interpolation never spins the long way round
        float yaw = camYaw + Mth.wrapDegrees(yawD - camYaw);
        apply(pos, yaw, Mth.clamp(pitchD, -90f, 90f), snap);
    }

    private static void apply(Vec3 pos, float yaw, float pitch, boolean snap) {
        double eye = dummy.getEyeHeight();
        if (snap) {
            dummy.xo = dummy.xOld = pos.x;
            dummy.yo = dummy.yOld = pos.y - eye;
            dummy.zo = dummy.zOld = pos.z;
            dummy.yRotO = yaw;
            dummy.xRotO = pitch;
        } else {
            dummy.xo = dummy.xOld = dummy.getX();
            dummy.yo = dummy.yOld = dummy.getY();
            dummy.zo = dummy.zOld = dummy.getZ();
            dummy.yRotO = dummy.getYRot();
            dummy.xRotO = dummy.getXRot();
        }
        dummy.setPos(pos.x, pos.y - eye, pos.z);
        dummy.setYRot(yaw);
        dummy.setXRot(pitch);
        camPos = pos;
        camYaw = yaw;
        camPitch = pitch;
    }

    // ------------------------------------------------------------ teardown

    private static void releaseCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (camActive && mc.player != null) mc.setCameraEntity(mc.player);
        camActive = false;
        dummy = null;
    }

    public static void reset() {
        boolean was = active;
        active = false;
        skippable = false;
        letterbox = false;
        fadeAlpha = fadeAlphaO = fadeFrom = fadeTo = 0;
        fadeTicks = fadeElapsed = 0;
        title = "";
        barProgress = barProgressO = 0;
        releaseCamera();
        Minecraft mc = Minecraft.getInstance();
        if (was && mc.screen == null && mc.player != null) mc.mouseHandler.grabMouse();
    }
}
