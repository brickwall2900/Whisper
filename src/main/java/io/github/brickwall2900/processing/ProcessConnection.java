package io.github.brickwall2900.processing;

import io.github.brickwall2900.processing.packets.Packet;
import io.netty.channel.Channel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ProcessConnection {
    private final UUID uuid;
    private final Process process;
    private @Nullable Channel channel;

    ProcessConnection(UUID uuid, Process process) {
        this.uuid = uuid;
        this.process = process;
    }

    public UUID getUUID() {
        return uuid;
    }

    public @Nullable Channel getChannel() {
        return channel;
    }

    void setChannel(@Nullable Channel channel) {
        this.channel = channel;
    }

    public boolean isConnected() {
        return channel != null;
    }

    public void sendPacket(Packet packet) {
        if (channel != null) {
            channel.writeAndFlush(packet);
        }
    }

    public Process getProcess() {
        return process;
    }
}
