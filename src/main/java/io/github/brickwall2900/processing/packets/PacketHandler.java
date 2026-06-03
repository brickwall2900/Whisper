package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

public record PacketHandler<P>(PacketReader<P> reader, PacketWriter<P> writer) {
    public interface PacketReader<P> {
        P decode(ByteBuf inBuffer);
    }
    public interface PacketWriter<P> {
        void encode(P packet, ByteBuf outBuffer);
    }
}
