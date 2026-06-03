package io.github.brickwall2900.processing.messaging.acceptors;

import java.util.Objects;
import java.util.UUID;

/// This is a `MessageAcceptor` that only accepts messages from channels with a specified filter
public record ChannelFilteringMessageAcceptor(String filter, MessageAcceptor acceptor) implements MessageAcceptor {
    public ChannelFilteringMessageAcceptor(String filter, MessageAcceptor acceptor) {
        this.acceptor = Objects.requireNonNull(acceptor, "acceptor == null");
        this.filter = filter;
    }

    /// Accepts the message if and only if the filter matches exactly with the received channel
    /// @see String#equals(Object)
    @Override
    public void accept(UUID messageId,
                       UUID processIdSender,
                       String channel,
                       MessageType messageType,
                       String message,
                       UUID messageReplying) {
        if (messageType != MessageType.CHANNEL
                || Objects.equals(filter, channel)) {
            acceptor.accept(messageId, processIdSender, channel, messageType, message, messageReplying);
        }
    }
}
