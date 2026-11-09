package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: player pressed ESC during a cutscene. */
public record CutsceneSkipPayload() implements CustomPacketPayload {
    public static final Type<CutsceneSkipPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "cutscene_skip"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneSkipPayload> CODEC = StreamCodec.unit(new CutsceneSkipPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
