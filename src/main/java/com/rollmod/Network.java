package com.rollmod;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class Network {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RollMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, RollPacket.class,
                RollPacket::encode, RollPacket::decode, RollPacket::handle);
        CHANNEL.registerMessage(1, RollSyncPacket.class,
                RollSyncPacket::encode, RollSyncPacket::decode, RollSyncPacket::handle);
        CHANNEL.registerMessage(2, RollStatusPacket.class,
                RollStatusPacket::encode, RollStatusPacket::decode, RollStatusPacket::handle);
    }
}
