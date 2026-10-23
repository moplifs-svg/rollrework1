package com.rollmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: roll key pressed, with the movement input at that moment. */
public class RollPacket {
    private final float forward;
    private final float left;

    public RollPacket(float forward, float left) {
        this.forward = forward;
        this.left = left;
    }

    public static void encode(RollPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.forward);
        buf.writeFloat(msg.left);
    }

    public static RollPacket decode(FriendlyByteBuf buf) {
        return new RollPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(RollPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                RollHandler.tryRoll(player, msg.forward, msg.left);
            }
        });
        context.setPacketHandled(true);
    }
}
