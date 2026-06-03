package io.github.brickwall2900.processing.packets;

import java.util.HashMap;
import java.util.Map;

public final class PacketRegistry {
    public static final Map<String, PacketHandler<? extends Packet>> REGISTRY;

    static {
        REGISTRY = new HashMap<>();
        REGISTRY.put(ShutdownRequestPacket.TYPE,
                new PacketHandler<>(
                        ShutdownRequestPacket::decode,
                        ShutdownRequestPacket::encode));
        REGISTRY.put(ShutdownAckPacket.TYPE,
                new PacketHandler<>(
                        ShutdownAckPacket::decode,
                        ShutdownAckPacket::encode));
        REGISTRY.put(UnencryptedWelcomePacket.TYPE,
                new PacketHandler<>(
                        UnencryptedWelcomePacket::decode,
                        UnencryptedWelcomePacket::encode));
        REGISTRY.put(SubscriptionStatusPacket.TYPE,
                new PacketHandler<>(
                        SubscriptionStatusPacket::decode,
                        SubscriptionStatusPacket::encode));
        REGISTRY.put(ChildShutdownRequestPacket.TYPE,
                new PacketHandler<>(
                        ChildShutdownRequestPacket::decode,
                        ChildShutdownRequestPacket::encode));
        REGISTRY.put(MessagePacket.TYPE,
                new PacketHandler<>(
                        MessagePacket::decode,
                        MessagePacket::encode));
        REGISTRY.put(ChildrenInitReplicationPacket.TYPE,
                new PacketHandler<>(
                        ChildrenInitReplicationPacket::decode,
                        ChildrenInitReplicationPacket::encode));
        REGISTRY.put(ChildReplicationPacket.TYPE,
                new PacketHandler<>(
                        ChildReplicationPacket::decode,
                        ChildReplicationPacket::encode));
        REGISTRY.put(ExceptionPacket.TYPE,
                new PacketHandler<>(
                        ExceptionPacket::decode,
                        ExceptionPacket::encode));
    }
}
