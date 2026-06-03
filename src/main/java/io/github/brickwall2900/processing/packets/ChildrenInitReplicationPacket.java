package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record ChildrenInitReplicationPacket(Set<UUID> childrenProcesses) implements Packet {
    public static final String TYPE = ChildrenInitReplicationPacket.class.getName();

    public static void encode(ChildrenInitReplicationPacket packet, ByteBuf buffer) {
        PacketUtils.writeSet(packet.childrenProcesses, buffer, PacketUtils::writeUUID);
    }

    public static ChildrenInitReplicationPacket decode(ByteBuf buffer) {
        return new ChildrenInitReplicationPacket(PacketUtils.readSet(HashSet::new, buffer, PacketUtils::readUUID));
    }

    @Override
    public String type() {
        return TYPE;
    }
}
