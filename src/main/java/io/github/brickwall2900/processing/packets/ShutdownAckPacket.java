package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

public record ShutdownAckPacket() implements Packet {
    public static final String TYPE = ShutdownAckPacket.class.getName();

    public static void encode(ShutdownAckPacket packet, ByteBuf buffer) {
    }

    public static ShutdownAckPacket decode(ByteBuf buffer) {
        return new ShutdownAckPacket();
    }

    @Override
    public String type() {
        return TYPE;
    }
}
