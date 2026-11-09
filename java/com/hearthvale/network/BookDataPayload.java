package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the data behind the Hearthvale book. open = also open the book screen. */
public record BookDataPayload(boolean open, CompoundTag data) implements CustomPacketPayload {
    public static final Type<BookDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "book_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookDataPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BookDataPayload::open,
            ByteBufCodecs.COMPOUND_TAG, BookDataPayload::data,
            BookDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
