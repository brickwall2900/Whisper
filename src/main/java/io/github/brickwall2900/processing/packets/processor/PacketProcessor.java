package io.github.brickwall2900.processing.packets.processor;

import io.github.brickwall2900.processing.packets.Packet;
import io.netty.channel.Channel;

/// this is something to process packets
/// to be processed by {@link PacketDispatcher}
public interface PacketProcessor<Pk extends Packet, Pr, C> {
    void process(Pk packet, Pr processor, C connection, Channel channel);
}
