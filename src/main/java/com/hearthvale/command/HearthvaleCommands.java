package com.hearthvale.command;

import com.hearthvale.book.BookDataBuilder;
import com.hearthvale.data.BuildingRegistry;
import com.hearthvale.network.BookDataPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import com.hearthvale.data.NpcDefinition;
import com.hearthvale.data.NpcRegistry;
import com.hearthvale.dialogue.NpcInteractions;
import com.hearthvale.entity.NpcEntity;
import com.hearthvale.util.GameTime;
import com.hearthvale.world.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/**
 * /hearthvale social | village | list                       (everyone)
 * /hearthvale hearts <npc> <points>                          (op) set friendship points (250 = 1 heart)
 * /hearthvale status <npc> <friend|dating|engaged|married>   (op)
 * /hearthvale arrive <npc>                                   (op) force an NPC to arrive at the nearest village
 * /hearthvale event <npc> <event_id>                         (op) replay an event on the nearest matching NPC
 */
public final class HearthvaleCommands {
    private static final SuggestionProvider<CommandSourceStack> NPCS = (ctx, b) ->
            SharedSuggestionProvider.suggest(NpcRegistry.all().stream().map(d -> d.id), b);
    private static final SuggestionProvider<CommandSourceStack> STATUSES = (ctx, b) ->
            SharedSuggestionProvider.suggest(List.of("friend", "dating", "engaged", "married"), b);

    private HearthvaleCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hearthvale")
                .then(Commands.literal("book").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PacketDistributor.sendToPlayer(p, new BookDataPayload(true, BookDataBuilder.build(p)));
                    return 1;
                }))
                .then(Commands.literal("social").executes(c -> {
                    NpcInteractions.journal(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("village").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ServerLevel level = p.serverLevel();
                    Village v = WorldState.get(level).nearest(level.dimension(), p.blockPosition(), 256);
                    if (v == null) c.getSource().sendFailure(Component.literal("No village within 256 blocks."));
                    else VillageManager.describe(p, v);
                    return 1;
                }))
                .then(Commands.literal("list").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("NPCs: " + NpcRegistry.all().stream().map(n -> n.id).toList()), false);
                    c.getSource().sendSuccess(() -> Component.literal("Building types: " + BuildingRegistry.all().stream().map(b -> b.id).toList()), false);
                    return 1;
                }))
                .then(Commands.literal("hearts").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("npc", ResourceLocationArgument.id()).suggests(NPCS)
                                .then(Commands.argument("points", IntegerArgumentType.integer(0, 3500)).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    NpcDefinition def = npc(c.getSource(), ResourceLocationArgument.getId(c, "npc").toString());
                                    if (def == null) return 0;
                                    Relationship r = WorldState.get(p.serverLevel()).player(p.getUUID()).rel(def.id);
                                    r.points = IntegerArgumentType.getInteger(c, "points");
                                    WorldState.get(p.serverLevel()).setDirty();
                                    c.getSource().sendSuccess(() -> Component.literal(def.name + " is now at " + r.hearts() + " hearts."), false);
                                    return 1;
                                }))))
                .then(Commands.literal("status").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("npc", ResourceLocationArgument.id()).suggests(NPCS)
                                .then(Commands.argument("status", StringArgumentType.word()).suggests(STATUSES).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    NpcDefinition def = npc(c.getSource(), ResourceLocationArgument.getId(c, "npc").toString());
                                    if (def == null) return 0;
                                    Relationship r = WorldState.get(p.serverLevel()).player(p.getUUID()).rel(def.id);
                                    try {
                                        r.status = Relationship.Status.valueOf(StringArgumentType.getString(c, "status").toUpperCase());
                                    } catch (IllegalArgumentException ex) {
                                        c.getSource().sendFailure(Component.literal("Use friend, dating, engaged or married."));
                                        return 0;
                                    }
                                    if (r.status == Relationship.Status.ENGAGED) r.weddingDay = GameTime.day(p.serverLevel());
                                    WorldState.get(p.serverLevel()).setDirty();
                                    c.getSource().sendSuccess(() -> Component.literal(def.name + " is now: " + r.status.label()), false);
                                    return 1;
                                }))))
                .then(Commands.literal("arrive").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("npc", ResourceLocationArgument.id()).suggests(NPCS).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ServerLevel level = p.serverLevel();
                            NpcDefinition def = npc(c.getSource(), ResourceLocationArgument.getId(c, "npc").toString());
                            if (def == null) return 0;
                            Village v = WorldState.get(level).nearest(level.dimension(), p.blockPosition(), 256);
                            if (v == null) {
                                c.getSource().sendFailure(Component.literal("No village within 256 blocks."));
                                return 0;
                            }
                            if (VillageManager.tryArrival(level, v, def.id) == null) {
                                c.getSource().sendFailure(Component.literal(def.name + " is already living somewhere, or no spawn spot was found."));
                                return 0;
                            }
                            return 1;
                        })))
                .then(Commands.literal("event").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("npc", ResourceLocationArgument.id()).suggests(NPCS)
                                .then(Commands.argument("event", StringArgumentType.word()).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    NpcDefinition def = npc(c.getSource(), ResourceLocationArgument.getId(c, "npc").toString());
                                    if (def == null) return 0;
                                    NpcEntity near = p.serverLevel().getEntitiesOfClass(NpcEntity.class, p.getBoundingBox().inflate(16),
                                                    e -> e.getNpcId().equals(def.id)).stream()
                                            .min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
                                    if (near == null) {
                                        c.getSource().sendFailure(Component.literal(def.name + " isn't within 16 blocks."));
                                        return 0;
                                    }
                                    if (!NpcInteractions.forceEvent(p, near, StringArgumentType.getString(c, "event"))) {
                                        c.getSource().sendFailure(Component.literal("No such event on " + def.name + "."));
                                        return 0;
                                    }
                                    return 1;
                                })))));
    }

    private static NpcDefinition npc(CommandSourceStack src, String id) {
        NpcDefinition d = NpcRegistry.get(id);
        if (d == null) src.sendFailure(Component.literal("Unknown NPC '" + id + "'. Try /hearthvale list"));
        return d;
    }
}
