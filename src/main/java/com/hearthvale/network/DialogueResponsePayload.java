package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: -1 = advance to next line, 0..n = chose option, -2 = player closed the screen. */
public record DialogueResponsePayload(int choice) implements CustomPacketPayload {

    public static final Type<DialogueResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "dialogue_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueResponsePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialogueResponsePayload::choice,
            DialogueResponsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
