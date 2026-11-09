package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: centred title card ("Later that evening..."). */
public record TitlePayload(String text, int ticks) implements CustomPacketPayload {
    public static final Type<TitlePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "title"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TitlePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TitlePayload::text,
            ByteBufCodecs.VAR_INT, TitlePayload::ticks,
            TitlePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
