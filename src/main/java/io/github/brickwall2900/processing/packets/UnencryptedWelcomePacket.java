package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

import java.util.Objects;
import java.util.UUID;

public record UnencryptedWelcomePacket(UUID processId) implements Packet {
    public static final String TYPE = UnencryptedWelcomePacket.class.getName();

    public UnencryptedWelcomePacket(UUID processId) {
        this.processId = Objects.requireNonNull(processId);
    }

    public static void encode(UnencryptedWelcomePacket packet, ByteBuf buffer) {
        PacketUtils.writeUUID(packet.processId, buffer);
    }

    public static UnencryptedWelcomePacket decode(ByteBuf buffer) {
        return new UnencryptedWelcomePacket(PacketUtils.readUUID(buffer));
    }

    @Override
    public String type() {
        return TYPE;
    }
}
