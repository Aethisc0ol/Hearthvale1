package com.hearthvale.api;

import com.google.gson.JsonObject;
import com.hearthvale.data.Effects;
import com.hearthvale.data.ScriptEffect;

import java.util.function.Function;

/**
 * Optional Java hook for addon MODS that want custom dialogue effects.
 * (Plain NPCs, buildings, dialogue and events need NO Java - just JSON datapacks.)
 *
 * Call from your mod constructor / common setup:
 * <pre>
 * HearthvaleApi.registerEffect("mymod:grant_xp", json -> {
 *     int amount = json.get("amount").getAsInt();
 *     return ctx -> ctx.player().giveExperiencePoints(amount);
 * });
 * </pre>
 * Then in any NPC script: {"say": "...", "effects": [{"type": "mymod:grant_xp", "amount": 50}]}
 */
public final class HearthvaleApi {
    private HearthvaleApi() {}

    public static void registerEffect(String id, Function<JsonObject, ScriptEffect> parser) {
        Effects.register(id, parser);
    }
}
