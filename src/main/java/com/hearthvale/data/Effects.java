package com.hearthvale.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hearthvale.Hearthvale;
import com.hearthvale.dialogue.NpcInteractions;
import com.hearthvale.util.Ids;
import com.hearthvale.util.Json;
import com.hearthvale.world.Relationship;
import com.hearthvale.world.VillageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry of effect types. Built-ins are registered below; other mods can add their own
 * through {@link com.hearthvale.api.HearthvaleApi#registerEffect}.
 *
 * In JSON an effect is either a shorthand key on a line/choice ("points", "flag", "give", "command",
 * "settle", "marry", "date", "engage") or an entry in an "effects" array: {"type": "friendship", "amount": 40}.
 */
public final class Effects {
    private static final Map<String, Function<JsonObject, ScriptEffect>> TYPES = new ConcurrentHashMap<>();

    static {
        register("friendship", o -> friendship(Json.integer(o, "amount", 0)));
        register("flag", o -> flag(Json.str(o, "name", "")));
        register("give_item", o -> give(Json.str(o, "item", "minecraft:air"), Json.integer(o, "count", 1)));
        register("command", o -> command(Json.str(o, "command", "")));
        register("settle", o -> settle());
        register("marry", o -> marry());
        register("start_dating", o -> date());
        register("engage", o -> engage());
    }

    private Effects() {}

    public static void register(String type, Function<JsonObject, ScriptEffect> parser) {
        TYPES.put(Ids.norm(type), parser);
    }

    public static ScriptEffect parse(JsonObject o) {
        String type = Ids.norm(Json.str(o, "type", ""));
        Function<JsonObject, ScriptEffect> f = TYPES.get(type);
        if (f == null) throw new IllegalArgumentException("Unknown effect type '" + type + "'");
        return f.apply(o);
    }

    /** Reads shorthand effect keys + an optional "effects" array from a line or choice object. */
    public static List<ScriptEffect> parseShorthand(JsonObject o) {
        List<ScriptEffect> out = new ArrayList<>();
        if (o.has("points")) out.add(friendship(Json.integer(o, "points", 0)));
        if (o.has("flag")) out.add(flag(Json.str(o, "flag", "")));
        if (o.has("give")) {
            JsonElement g = o.get("give");
            if (g.isJsonObject()) out.add(give(Json.str(g.getAsJsonObject(), "item", "minecraft:air"), Json.integer(g.getAsJsonObject(), "count", 1)));
            else out.add(give(g.getAsString(), 1));
        }
        if (o.has("command")) out.add(command(Json.str(o, "command", "")));
        if (Json.bool(o, "settle", false)) out.add(settle());
        if (Json.bool(o, "marry", false)) out.add(marry());
        if (Json.bool(o, "date", false)) out.add(date());
        if (Json.bool(o, "engage", false)) out.add(engage());
        for (JsonElement e : Json.list(o, "effects")) {
            if (e.isJsonObject()) out.add(parse(e.getAsJsonObject()));
        }
        return out;
    }

    // ---- built-in effect factories (also used by DefaultLines) ----

    public static ScriptEffect friendship(int amount) {
        return c -> NpcInteractions.addPoints(c.player(), c.npc(), c.def(), c.rel(), amount);
    }

    public static ScriptEffect flag(String name) {
        return c -> { c.data().flags.add(name); c.touch(); };
    }

    public static ScriptEffect give(String itemId, int count) {
        return c -> {
            ResourceLocation rl = ResourceLocation.tryParse(Ids.norm(itemId));
            Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) {
                Hearthvale.LOGGER.warn("give effect: unknown item '{}'", itemId);
                return;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, count));
            if (!c.player().getInventory().add(stack)) c.player().drop(stack, false);
        };
    }

    public static ScriptEffect command(String cmd) {
        return c -> {
            var server = c.player().getServer();
            if (server == null || cmd.isBlank()) return;
            var source = c.player().createCommandSourceStack().withPermission(2).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, cmd);
        };
    }

    public static ScriptEffect settle() {
        return c -> VillageManager.settle(c.npc(), c.player());
    }

    public static ScriptEffect marry() {
        return c -> NpcInteractions.marry(c.player(), c.npc(), c.def());
    }

    public static ScriptEffect date() {
        return c -> {
            Relationship r = c.rel();
            if (r.status == Relationship.Status.FRIEND) r.status = Relationship.Status.DATING;
            c.touch();
        };
    }

    public static ScriptEffect engage() {
        return c -> {
            Relationship r = c.rel();
            r.status = Relationship.Status.ENGAGED;
            r.weddingDay = com.hearthvale.util.GameTime.day(c.level()) + 3;
            c.touch();
        };
    }
}
