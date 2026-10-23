package com.rollmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> owner: current roll charges for the HUD. */
public class RollStatusPacket {
    private final int charges;
    private final int recharge;
    private final int cooldown;

    public RollStatusPacket(int charges, int recharge, int cooldown) {
        this.charges = charges;
        this.recharge = recharge;
        this.cooldown = cooldown;
    }

    public static void encode(RollStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.charges);
        buf.writeVarInt(msg.recharge);
        buf.writeVarInt(msg.cooldown);
    }

    public static RollStatusPacket decode(FriendlyByteBuf buf) {
        return new RollStatusPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(RollStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> RollClient.onStatus(msg.charges, msg.recharge, msg.cooldown)));
        context.setPacketHandled(true);
    }
}
