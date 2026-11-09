package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server -> client: show this dialogue page. Empty text means "close the dialogue screen". */
public record DialoguePagePayload(int entityId, String npcId, String speaker, String text, List<String> choices, int points)
        implements CustomPacketPayload {

    public static final Type<DialoguePagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "dialogue_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DialoguePagePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialoguePagePayload::entityId,
            ByteBufCodecs.STRING_UTF8, DialoguePagePayload::npcId,
            ByteBufCodecs.STRING_UTF8, DialoguePagePayload::speaker,
            ByteBufCodecs.STRING_UTF8, DialoguePagePayload::text,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DialoguePagePayload::choices,
            ByteBufCodecs.VAR_INT, DialoguePagePayload::points,
            DialoguePagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
