package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: a cutscene started/ended (locks input, shows letterbox bars). */
public record CutsceneStatePayload(boolean active, boolean skippable, boolean letterbox) implements CustomPacketPayload {
    public static final Type<CutsceneStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "cutscene_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CutsceneStatePayload::active,
            ByteBufCodecs.BOOL, CutsceneStatePayload::skippable,
            ByteBufCodecs.BOOL, CutsceneStatePayload::letterbox,
            CutsceneStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
