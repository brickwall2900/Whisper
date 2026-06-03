package io.github.brickwall2900.processing.messaging.impl;

import io.github.brickwall2900.processing.ProcessConnection;
import io.github.brickwall2900.processing.ProcessManagerChild;
import io.github.brickwall2900.processing.messaging.EventConnection;
import io.github.brickwall2900.processing.messaging.Messenger;
import io.github.brickwall2900.processing.messaging.acceptors.MessageAcceptor;
import io.github.brickwall2900.processing.packets.MessagePacket;
import io.github.brickwall2900.processing.packets.Packet;
import io.github.brickwall2900.processing.packets.SubscriptionStatusPacket;
import io.github.brickwall2900.processing.packets.processor.PacketProcessor;
import io.netty.channel.Channel;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChildMessenger extends Messenger
        implements PacketProcessor<Packet, ProcessManagerChild, ProcessConnection> {
    private final Set<MessageAcceptor> listeners = ConcurrentHashMap.newKeySet();

    public ChildMessenger(ProcessManagerChild processManager) {
        super(processManager);
    }

    @Override
    public void subscribe(String channel) {
        processManager.asChild().sendPacket(new SubscriptionStatusPacket(channel, true));
    }

    @Override
    public void unsubscribe(String channel) {
        processManager.asChild().sendPacket(new SubscriptionStatusPacket(channel, false));
    }

    private final class ChildEventConnection implements EventConnection {
        private MessageAcceptor acceptor;

        private ChildEventConnection(MessageAcceptor acceptor) {
            this.acceptor = acceptor;
        }

        @Override
        public boolean isConnected() {
            return acceptor != null;
        }

        @Override
        public void disconnect() {
            listeners.remove(acceptor);
            acceptor = null;
        }
    }

    @Override
    public EventConnection on(MessageAcceptor onMessage) {
        listeners.add(onMessage);
        return new ChildEventConnection(onMessage);
    }

    private void sendMessage(MessagePacket packet) {
        processManager.asChild().sendPacket(packet);
    }

    @Override
    public UUID messagePublish(String channel, String messageContent) {
        MessagePacket packet = new MessagePacket(
                messageContent,
                MessagePacket.DeliveryMode.CHANNEL,
                currentProcessId,
                null,
                channel,
                null
        );

        sendMessage(packet);

        return packet.messageId();
    }

    @Override
    public UUID messageDirectReply(UUID processIdRecipient, String messageContent, UUID messageReplyTo) {
        if (Objects.equals(processIdRecipient, currentProcessId)) {
            return null;
        }

        MessagePacket packet = new MessagePacket(
                messageContent,
                MessagePacket.DeliveryMode.DIRECT,
                currentProcessId,
                processIdRecipient,
                null,
                messageReplyTo
        );

        sendMessage(packet);

        return packet.messageId();
    }

    @Override
    public UUID messageBroadcast(String message) {
        MessagePacket packet = new MessagePacket(
                message,
                MessagePacket.DeliveryMode.BROADCAST,
                currentProcessId,
                null,
                null,
                null
        );

        sendMessage(packet);

        return packet.messageId();
    }

    @Override
    public void process(Packet packet,
                        ProcessManagerChild processor,
                        ProcessConnection connection,
                        Channel channel) {
        if (packet instanceof MessagePacket messagePacket) {
            handleMessage(messagePacket);
        }
    }

    private void handleMessage(MessagePacket packet) {
        switch (packet.deliveryMode()) {
            case CHANNEL -> handleChannelMessage(packet);
            case BROADCAST -> handleBroadcastMessage(packet);
            case DIRECT -> handleDirectMessage(packet);
        }
    }

    private void handleDirectMessage(MessagePacket packet) {
        for (MessageAcceptor acceptor : listeners) {
            acceptor.accept(
                    packet.messageId(),
                    packet.sender(),
                    null,
                    MessageAcceptor.MessageType.DIRECT,
                    packet.message(),
                    packet.replyToMessage()
            );
        }
    }

    private void handleBroadcastMessage(MessagePacket packet) {
        for (MessageAcceptor acceptor : listeners) {
            acceptor.accept(
                    packet.messageId(),
                    packet.sender(),
                    null,
                    MessageAcceptor.MessageType.BROADCAST,
                    packet.message(),
                    packet.replyToMessage()
            );
        }
    }

    private void handleChannelMessage(MessagePacket packet) {
        String channel = packet.channel();
        if (listeners != null) {
            for (MessageAcceptor acceptor : listeners) {
                acceptor.accept(
                        packet.messageId(),
                        packet.sender(),
                        channel,
                        MessageAcceptor.MessageType.CHANNEL,
                        packet.message(),
                        packet.replyToMessage()
                );
            }
        }
    }
}
