package io.github.brickwall2900.processing.demo.chat;

import io.github.brickwall2900.processing.messaging.acceptors.MessageAcceptor;

import java.util.Objects;
import java.util.UUID;

public record TabInfo(MessageAcceptor.MessageType type, int tabIndex, String channel, UUID target) {
    public boolean matches(MessageAcceptor.MessageType type, String channel, UUID target) {
        return switch (type) {
            case CHANNEL -> Objects.equals(this.channel, channel);
            case DIRECT -> Objects.equals(this.target, target);
            case BROADCAST -> true;
        };
    }
}
