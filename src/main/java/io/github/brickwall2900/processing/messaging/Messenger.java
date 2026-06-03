package io.github.brickwall2900.processing.messaging;

import io.github.brickwall2900.processing.ProcessManager;
import io.github.brickwall2900.processing.messaging.acceptors.MessageAcceptor;
import io.github.brickwall2900.processing.packets.MessagePacket;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/// Messenger allows easy communication between processes.
///
/// In instant-messaging terms:
/// * A "direct" communication is like a DM with someone
/// * A "channel" is like a group chat
/// * and a "broadcast" is like an entire server
///
/// Messenger takes into context the current process ID and constructs
/// {@link MessagePacket}s to be sent.
///
/// @implSpec The master process is responsible for handling and routing messages to other child processes.
///
/// The master should also be careful not to send messages to the process who sent the message.
public abstract class Messenger {
    protected final ProcessManager processManager;
    protected final UUID currentProcessId;

    public Messenger(ProcessManager processManager) {
        this.processManager = processManager;
        this.currentProcessId = processManager.getMyId();
    }

    /// subscribe this process ID to the specified channel where messages can go through
    public abstract void subscribe(String channel);

    /// un-subscribe this process ID to the specified channel where messages can go through
    public abstract void unsubscribe(String channel);

    /// listens to any messages sent
    ///
    /// @return an event connection for disconnecting the listener
    public abstract EventConnection on(MessageAcceptor onMessage);

    /// sends a message to the specified channel
    ///
    /// @implSpec delivery mode is {@link MessagePacket.DeliveryMode#CHANNEL}
    /// @return the message ID, `null` if the message didn't send
    public abstract @Nullable UUID messagePublish(String channel, String message);

    /// sends a message directly to the specified process ID
    ///
    /// attempting to send direct messages to yourself will do nothing
    ///
    /// @implSpec delivery mode is {@link MessagePacket.DeliveryMode#DIRECT}
    /// @implNote calls {@link Messenger#messageDirectReply(UUID, String, UUID)} with messageReplyTo set to `null`
    /// @return the message ID, `null` if the message didn't send
    public @Nullable UUID messageDirect(UUID processIdRecipient, String message) {
        return messageDirectReply(processIdRecipient, message, null);
    }

    /// replies to specified message UUID with the message content directly to the specified recipient
    ///
    /// attempting to send direct messages to yourself will do nothing
    /// @implSpec delivery mode is {@link MessagePacket.DeliveryMode#DIRECT}
    /// @apiNote sending a reply from a non-existent message to a recipient may cause some issues!
    /// who knows?! the target you're sending it onto may or may NOT have an idea what message you're replying to...
    /// @return the message ID, `null` if the message didn't send
    public abstract @Nullable UUID messageDirectReply(UUID processIdRecipient, String messageContent, UUID messageReplyTo);

    /// broadcasts a message for every process to hear
    ///
    /// @implSpec delivery mode is {@link MessagePacket.DeliveryMode#BROADCAST}
    /// @return the message ID, `null` if the message didn't send
    public abstract @Nullable UUID messageBroadcast(String message);
}
