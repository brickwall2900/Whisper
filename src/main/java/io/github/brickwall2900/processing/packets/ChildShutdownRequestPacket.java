package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

public record ChildShutdownRequestPacket(String reason, int exitCode) implements Packet {
    public static final String TYPE = ChildShutdownRequestPacket.class.getName();

    public static void encode(ChildShutdownRequestPacket packet, ByteBuf buffer) {
        PacketUtils.writeNullable(packet.reason, buffer, PacketUtils::writeString);
        buffer.writeInt(packet.exitCode);
    }

    public static ChildShutdownRequestPacket decode(ByteBuf buffer) {
        return new ChildShutdownRequestPacket(
                PacketUtils.readNullable(buffer, PacketUtils::readString),
                buffer.readInt()
        );
    }

    @Override
    public String type() {
        return TYPE;
    }
}
