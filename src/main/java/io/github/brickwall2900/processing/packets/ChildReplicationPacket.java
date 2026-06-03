package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

import java.util.UUID;

public record ChildReplicationPacket(UUID processId, boolean added) implements Packet {
    public static final String TYPE = ChildReplicationPacket.class.getName();

    public static void encode(ChildReplicationPacket packet, ByteBuf buffer) {
        PacketUtils.writeUUID(packet.processId, buffer);
        buffer.writeBoolean(packet.added);
    }

    public static ChildReplicationPacket decode(ByteBuf buffer) {
        return new ChildReplicationPacket(PacketUtils.readUUID(buffer), buffer.readBoolean());
    }

    @Override
    public String type() {
        return TYPE;
    }
}
