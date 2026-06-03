package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.Objects;

public final class PacketDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        String type = PacketUtils.readString(in);

        PacketHandler<? extends Packet> handler = PacketRegistry.REGISTRY.get(type);
        Objects.requireNonNull(handler, type + " handler not found");

        out.add(handler.reader().decode(in));
    }
}
