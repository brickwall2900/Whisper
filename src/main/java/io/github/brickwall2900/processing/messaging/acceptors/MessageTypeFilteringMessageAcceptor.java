package io.github.brickwall2900.processing.messaging.acceptors;

import java.util.Objects;
import java.util.UUID;

/// This is a `MessageAcceptor` that only accepts messages with only the specified {@link MessageAcceptor.MessageType}
public record MessageTypeFilteringMessageAcceptor(MessageType filter, MessageAcceptor acceptor) implements MessageAcceptor {
    public MessageTypeFilteringMessageAcceptor(MessageType filter, MessageAcceptor acceptor) {
        this.acceptor = Objects.requireNonNull(acceptor, "acceptor == null");
        this.filter = Objects.requireNonNull(filter, "filter == null");
    }

    /// Accepts the message if and only if the filter matches exactly with the received message type
    /// @see String#equals(Object)
    @Override
    public void accept(UUID messageId,
                       UUID processIdSender,
                       String channel,
                       MessageType messageType,
                       String message,
                       UUID messageReplying) {
        if (filter == messageType) {
            acceptor.accept(messageId, processIdSender, channel, messageType, message, messageReplying);
        }
    }
}
