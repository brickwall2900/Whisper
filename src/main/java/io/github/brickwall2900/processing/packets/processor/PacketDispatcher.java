package io.github.brickwall2900.processing.packets.processor;

import io.github.brickwall2900.processing.packets.Packet;
import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PacketDispatcher<P, C> {
    private final Map<String, PacketProcessor<?, P, C>> handlers = new HashMap<>();

    public <Pk extends Packet> void register(String packetType, PacketProcessor<Pk, P, C> handler) {
        if (handlers.containsKey(packetType)) {
            throw new IllegalArgumentException("Packet type " + packetType + " already registered");
        }
        handlers.put(packetType, handler);
    }

    @SuppressWarnings("unchecked")
    public <Pk extends Packet> void dispatch(Pk packet, P processor, C connection, Channel channel) {
        PacketProcessor<Pk, P, C> packetProcessor = (PacketProcessor<Pk, P, C>) handlers.get(packet.type());
        Objects.requireNonNull(packetProcessor, "No handler found for packet type " + packet.type());

        packetProcessor.process(packet, processor, connection, channel);
    }
}
