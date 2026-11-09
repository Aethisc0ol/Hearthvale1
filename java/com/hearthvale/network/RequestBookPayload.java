package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: send me the book data (and open the book if open = true). */
public record RequestBookPayload(boolean open) implements CustomPacketPayload {
    public static final Type<RequestBookPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "request_book"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestBookPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RequestBookPayload::open, RequestBookPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
