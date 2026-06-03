package io.github.brickwall2900.processing.messaging.acceptors;

import java.util.UUID;

/// `MessageAcceptor` accepts any message from any subscribed channel
/// @see ChannelFilteringMessageAcceptor
/// @see MessageTypeFilteringMessageAcceptor
@FunctionalInterface
public interface MessageAcceptor {
    /// @param messageId the message identifier, useful if you want to reply to messages
    /// @param processIdSender the process ID who sent this message
    /// @param channel the source of where it came from.
    /// the value `null` means it's either a direct or broadcast message
    /// @param messageType the type of message that is sent
    /// @param message the message itself
    void accept(UUID messageId,
                UUID processIdSender,
                String channel,
                MessageType messageType,
                String message,
                UUID replyMessage);

    enum MessageType {
        BROADCAST, CHANNEL, DIRECT
    }
}
