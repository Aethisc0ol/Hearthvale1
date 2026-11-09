package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: fade the screen to a colour (alpha 1) or back to clear (alpha 0). color -1 keeps the current colour. */
public record FadePayload(int color, float alpha, int ticks) implements CustomPacketPayload {
    public static final Type<FadePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "fade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FadePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FadePayload::color,
            ByteBufCodecs.FLOAT, FadePayload::alpha,
            ByteBufCodecs.VAR_INT, FadePayload::ticks,
            FadePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
