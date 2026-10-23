package com.rollmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: an entity started a roll in world direction (dx, dz). */
public class RollSyncPacket {
    private final int entityId;
    private final float dx;
    private final float dz;

    public RollSyncPacket(int entityId, float dx, float dz) {
        this.entityId = entityId;
        this.dx = dx;
        this.dz = dz;
    }

    public static void encode(RollSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeFloat(msg.dx);
        buf.writeFloat(msg.dz);
    }

    public static RollSyncPacket decode(FriendlyByteBuf buf) {
        return new RollSyncPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(RollSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> RollClient.onRoll(msg.entityId, msg.dx, msg.dz)));
        context.setPacketHandled(true);
    }
}
