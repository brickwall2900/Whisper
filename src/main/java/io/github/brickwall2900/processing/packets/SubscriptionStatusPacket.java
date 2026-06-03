package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

import java.util.Objects;

public record SubscriptionStatusPacket(String channel, boolean subscribe) implements Packet {
    public static final String TYPE = SubscriptionStatusPacket.class.getName();

    public SubscriptionStatusPacket(String channel, boolean subscribe) {
        this.channel = Objects.requireNonNull(channel);
        this.subscribe = subscribe;
    }

    public static void encode(SubscriptionStatusPacket packet, ByteBuf buffer) {
        PacketUtils.writeString(packet.channel, buffer);
        buffer.writeBoolean(packet.subscribe);
    }

    public static SubscriptionStatusPacket decode(ByteBuf buffer) {
        return new SubscriptionStatusPacket(
                PacketUtils.readString(buffer),
               buffer.readBoolean()
        );
    }

    @Override
    public String type() {
        return TYPE;
    }
}
