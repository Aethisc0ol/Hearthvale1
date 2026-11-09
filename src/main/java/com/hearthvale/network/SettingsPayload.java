package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: an operator changed a village setting in the book. Only whitelisted keys are applied. */
public record SettingsPayload(String key, int value) implements CustomPacketPayload {
    public static final Type<SettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SettingsPayload::key,
            ByteBufCodecs.VAR_INT, SettingsPayload::value,
            SettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
