package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public final class PacketEncoder extends MessageToByteEncoder<Packet> {
    @SuppressWarnings("unchecked")
    @Override
    protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) {
        String type = msg.type();
        PacketHandler<Packet> handler = (PacketHandler<Packet>) PacketRegistry.REGISTRY.get(type);

        PacketUtils.writeString(type, out);
        handler.writer().encode(msg, out);
    }
}
