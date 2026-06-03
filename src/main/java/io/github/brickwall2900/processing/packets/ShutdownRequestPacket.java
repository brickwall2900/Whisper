package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

public record ShutdownRequestPacket(int exitCode) implements Packet {
    public static final String TYPE = ShutdownRequestPacket.class.getName();

    public static void encode(ShutdownRequestPacket packet, ByteBuf buffer) {
        buffer.writeInt(packet.exitCode());
    }

    public static ShutdownRequestPacket decode(ByteBuf buffer) {
        return new ShutdownRequestPacket(buffer.readInt());
    }

    @Override
    public String type() {
        return TYPE;
    }
}
