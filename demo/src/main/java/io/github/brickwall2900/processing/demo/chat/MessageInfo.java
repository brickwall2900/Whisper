package io.github.brickwall2900.processing.demo.chat;

import io.github.brickwall2900.processing.messaging.acceptors.MessageAcceptor;

import java.util.UUID;

public record MessageInfo(UUID messageId,
                          UUID processIdSender,
                          String channel,
                          MessageAcceptor.MessageType messageType,
                          String message,
                          UUID replyingTo) {
}
