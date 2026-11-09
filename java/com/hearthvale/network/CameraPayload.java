package com.hearthvale.network;

import com.hearthvale.Hearthvale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client camera instruction. The client owns a dummy camera entity and animates it smoothly every frame.
 * mode 0 = move/look (over `ticks`), mode 1 = give the camera back to the player.
 * lookType: 0 keep current look, 1 fixed yaw/pitch, 2 look at a point, 3 follow an entity (by id).
 */
public record CameraPayload(int mode, boolean hasPos, double x, double y, double z,
                            int lookType, float yaw, float pitch, double lx, double ly, double lz,
                            int entityId, int ticks, boolean smooth) implements CustomPacketPayload {

    public static final Type<CameraPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hearthvale.MODID, "camera"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraPayload> CODEC =
            StreamCodec.of((RegistryFriendlyByteBuf buf, CameraPayload p) -> p.write(buf), CameraPayload::read);

    public static CameraPayload reset() {
        return new CameraPayload(1, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0, false);
    }

    private void write(RegistryFriendlyByteBuf b) {
        b.writeVarInt(mode);
        b.writeBoolean(hasPos);
        b.writeDouble(x); b.writeDouble(y); b.writeDouble(z);
        b.writeVarInt(lookType);
        b.writeFloat(yaw); b.writeFloat(pitch);
        b.writeDouble(lx); b.writeDouble(ly); b.writeDouble(lz);
        b.writeInt(entityId);
        b.writeVarInt(ticks);
        b.writeBoolean(smooth);
    }

    private static CameraPayload read(RegistryFriendlyByteBuf b) {
        return new CameraPayload(b.readVarInt(), b.readBoolean(), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readVarInt(), b.readFloat(), b.readFloat(), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readInt(), b.readVarInt(), b.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
