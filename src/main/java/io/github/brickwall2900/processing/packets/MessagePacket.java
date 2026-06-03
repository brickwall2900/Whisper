package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;

import java.util.Objects;
import java.util.UUID;

/// A message packet contains information for messaging between processes
///
/// * A master process may "broadcast" on all child processes
/// * A child process may "directly" message another child process or the master itself
/// * A child process may also listen and send messages to "channels" for others to hear
///
/// @param messageId the message ID.
/// each message has a corresponding ID, so that it can be replied back to.
/// @param message the message payload. message payloads are of type {@link String} to keep it simple...
/// @param deliveryMode the delivery mode of this message.
/// @param target the target this message will be sent to.
/// this is only valid iff {@code deliveryMode} is in {@link DeliveryMode#DIRECT}
/// @param channel the channel this message is broadcasting.
/// this is only valid iff {@code deliveryMode} is in {@link DeliveryMode#CHANNEL}
/// @param replyToMessage the message ID it is replying to.
/// this is only valid iff {@code deliveryMode} is in {@link DeliveryMode#DIRECT}
public record MessagePacket(UUID messageId,
                            String message,
                            DeliveryMode deliveryMode,
                            UUID sender,
                            UUID target,
                            String channel,
                            UUID replyToMessage) implements Packet {
    public MessagePacket(UUID messageId,
                         String message,
                         DeliveryMode deliveryMode,
                         UUID sender,
                         UUID target,
                         String channel,
                         UUID replyToMessage) {
        this.messageId = Objects.requireNonNull(messageId, "messageId == null");
        this.message = Objects.requireNonNull(message, "message == null");
        this.deliveryMode = Objects.requireNonNull(deliveryMode, "deliveryMode == null");
        this.sender = Objects.requireNonNull(sender, "sender == null");
        this.target = target;
        this.channel = channel;
        this.replyToMessage = replyToMessage;
    }

    /// A message packet constructor that generate a random UUID as its message ID
    public MessagePacket(String message,
                         DeliveryMode deliveryMode,
                         UUID sender,
                         UUID target,
                         String channel,
                         UUID replyToMessage) {
        this(UUID.randomUUID(), message, deliveryMode, sender, target, channel, replyToMessage);
    }

    public static final String TYPE = MessagePacket.class.getName();

    public static void encode(MessagePacket packet, ByteBuf buffer) {
        PacketUtils.writeUUID(packet.messageId, buffer);
        PacketUtils.writeString(packet.message, buffer);
        PacketUtils.writeString(packet.deliveryMode.name(), buffer);
        PacketUtils.writeUUID(packet.sender, buffer);
        PacketUtils.writeNullable(packet.target, buffer, PacketUtils::writeUUID);
        PacketUtils.writeNullable(packet.channel, buffer, PacketUtils::writeString);
        PacketUtils.writeNullable(packet.replyToMessage, buffer, PacketUtils::writeUUID);
    }

    public static MessagePacket decode(ByteBuf buffer) {
        return new MessagePacket(
                PacketUtils.readUUID(buffer),
                PacketUtils.readString(buffer),
                DeliveryMode.valueOf(PacketUtils.readString(buffer)),
                PacketUtils.readUUID(buffer),
                PacketUtils.readNullable(buffer, PacketUtils::readUUID),
                PacketUtils.readNullable(buffer, PacketUtils::readString),
                PacketUtils.readNullable(buffer, PacketUtils::readUUID)
        );
    }

    @Override
    public String type() {
        return TYPE;
    }

    public enum DeliveryMode {
        CHANNEL,
        DIRECT,
        BROADCAST
    }
}