package io.github.brickwall2900.processing.packets;

import io.github.brickwall2900.processing.ExceptionInfo;
import io.netty.buffer.ByteBuf;

import java.util.Objects;
import java.util.UUID;

public record ExceptionPacket(ExceptionInfo info) implements Packet {
    public static final String TYPE = ExceptionPacket.class.getName();

    public ExceptionPacket(ExceptionInfo info) {
        this.info = Objects.requireNonNull(info);
    }

    public static void encode(ExceptionPacket packet, ByteBuf buffer) {
        Objects.requireNonNull(packet.info);
        write(packet.info, buffer);
    }

    private static void write(ExceptionInfo info, ByteBuf buffer) {
        buffer.writeBoolean(info != null);
        if (info != null) {
            PacketUtils.writeUUID(info.source(), buffer);
            PacketUtils.writeString(info.type(), buffer);
            PacketUtils.writeString(info.message(), buffer);
            PacketUtils.writeStringArray(info.stacktrace(), buffer);
            write(info, buffer);
        }
    }

    public static ExceptionPacket decode(ByteBuf buffer) {
        return new ExceptionPacket(read(buffer));
    }

    private static ExceptionInfo read(ByteBuf buffer) {
        boolean isNotNull = buffer.readBoolean();
        if (isNotNull) {
            UUID source = PacketUtils.readUUID(buffer);
            String type = PacketUtils.readString(buffer);
            String message = PacketUtils.readString(buffer);
            String[] stacktrace = PacketUtils.readStringArray(buffer);
            ExceptionInfo cause = read(buffer);
            return new ExceptionInfo(source, type, message, stacktrace, cause);
        } else {
            return null;
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

}
