package io.github.brickwall2900.processing.messaging.impl;

import io.github.brickwall2900.processing.ProcessConnection;
import io.github.brickwall2900.processing.ProcessManagerMaster;
import io.github.brickwall2900.processing.messaging.EventConnection;
import io.github.brickwall2900.processing.messaging.Messenger;
import io.github.brickwall2900.processing.messaging.acceptors.MessageAcceptor;
import io.github.brickwall2900.processing.packets.MessagePacket;
import io.github.brickwall2900.processing.packets.Packet;
import io.github.brickwall2900.processing.packets.SubscriptionStatusPacket;
import io.github.brickwall2900.processing.packets.processor.PacketProcessor;
import io.netty.channel.Channel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MasterMessenger extends Messenger
        implements PacketProcessor<Packet, ProcessManagerMaster, ProcessConnection> {
    private final Map<String, Set<UUID>> channelSubscriptions = new ConcurrentHashMap<>();
    private final Set<MessageAcceptor> listeners = ConcurrentHashMap.newKeySet();

    public MasterMessenger(ProcessManagerMaster processManager) {
        super(processManager);
    }

    @Override
    public void subscribe(String channel) {
        // we add our own process ID so when enumerating those who subscribed
        // it'll fire our listeners
        getOrCreateChannel(channel).add(currentProcessId);
    }

    @Override
    public void unsubscribe(String channel) {
        // do the same here
        Set<UUID> subscribers = getChannel(channel);
        if (subscribers != null) {
            subscribers.remove(currentProcessId);
        }
    }

    private static final class MasterEventConnection implements EventConnection {
        private MessageAcceptor listener;

        private MasterEventConnection(MessageAcceptor listener) {
            this.listener = listener;
        }

        @Override
        public boolean isConnected() {
            return listener != null;
        }

        @Override
        public void disconnect() {
            listener = null;
        }
    }

    @Override
    public EventConnection on(MessageAcceptor onMessage) {
        listeners.add(onMessage);
        return new MasterEventConnection(onMessage);
    }

    private void sendMessage(UUID processId, MessagePacket packet) {
        processManager.asMaster().sendPacket(processId, packet);
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

        routeMessage(packet, currentProcessId);

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

        routeMessage(packet, currentProcessId);

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

        routeMessage(packet, currentProcessId);

        return packet.messageId();
    }

    @Override
    public void process(Packet packet,
                        ProcessManagerMaster processor,
                        ProcessConnection connection,
                        Channel channel) {
        if (packet instanceof MessagePacket messagePacket) {
            routeMessage(messagePacket, connection.getUUID());
        } else if (packet instanceof SubscriptionStatusPacket subscriptionStatusPacket) {
            handleSubscriptionStatus(subscriptionStatusPacket, connection);
        }
    }

    private Set<UUID> getOrCreateChannel(String channel) {
        return channelSubscriptions.computeIfAbsent(channel, c -> ConcurrentHashMap.newKeySet());
    }

    private Set<UUID> getChannel(String channel) {
        return channelSubscriptions.get(channel);
    }

    /// routes MessagePackets.
    /// if the packets are directed towards myself (master), process them instead of sending back.
    private void routeMessage(MessagePacket packet, UUID senderProcessId) {
        switch (packet.deliveryMode()) {
            case CHANNEL -> routeChannelMessage(packet, senderProcessId);
            case BROADCAST -> routeBroadcast(packet, senderProcessId);
            case DIRECT -> routeDirectMessage(packet, senderProcessId);
        }
    }

    private void routeDirectMessage(MessagePacket packet, UUID senderProcessId) {
        UUID targetProcessId = packet.target();
        if (Objects.equals(senderProcessId, targetProcessId)) {
            return;
        }

        if (!processManager.isConnected(targetProcessId) && !Objects.equals(targetProcessId, currentProcessId)) {
            return;
        }

        if (Objects.equals(targetProcessId, currentProcessId)) {
            notifyOurselvesOfDirectMessage(packet);
        } else {
            sendMessage(targetProcessId, packet);
        }
    }

    private void notifyOurselvesOfDirectMessage(MessagePacket packet) {
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

    private void routeBroadcast(MessagePacket packet, UUID senderProcessId) {
        if (!Objects.equals(currentProcessId, senderProcessId)) {
            notifyOurselvesOfBroadcastMessage(packet);
        }

        for (UUID processId : processManager.getProcesses()) {
            if (!Objects.equals(processId, senderProcessId)) {
                sendMessage(processId, packet);
            }
        }
    }

    private void notifyOurselvesOfBroadcastMessage(MessagePacket packet) {
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

    private void routeChannelMessage(MessagePacket packet, UUID senderProcessId) {
        String channel = packet.channel();
        Set<UUID> subscribers = getChannel(channel);

        if (subscribers == null) {
            return;
        }

        for (Iterator<UUID> iterator = subscribers.iterator(); iterator.hasNext(); ) {
            UUID subscriber = iterator.next();

            if (!processManager.isConnected(subscriber) && !Objects.equals(subscriber, currentProcessId)) {
                // if the subscriber is no longer connected to the master
                // and if that subscriber is not ourselves (the master)
                // then we remove it
                iterator.remove();
                continue;
            }

            if (Objects.equals(subscriber, currentProcessId)
                    && !Objects.equals(currentProcessId, senderProcessId)) {
                // notify ourselves if we aren't the source
                notifyOurselvesOfChannelMessage(packet, channel);
            } else if (!Objects.equals(subscriber, senderProcessId)) {
                sendMessage(subscriber, packet);
            }
        }
    }

    private void notifyOurselvesOfChannelMessage(MessagePacket packet, String channel) {
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

    private void handleSubscriptionStatus(SubscriptionStatusPacket packet, ProcessConnection connection) {
        UUID processId = connection.getUUID();
        Set<UUID> channel = getOrCreateChannel(packet.channel());
        if (packet.subscribe()) {
            channel.add(processId);
        } else {
            channel.remove(processId);
        }
    }
}
