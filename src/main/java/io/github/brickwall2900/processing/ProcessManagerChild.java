package io.github.brickwall2900.processing;

import io.github.brickwall2900.processing.messaging.Messenger;
import io.github.brickwall2900.processing.messaging.impl.ChildMessenger;
import io.github.brickwall2900.processing.netty.NettyClientService;
import io.github.brickwall2900.processing.packets.*;
import io.github.brickwall2900.processing.packets.processor.PacketDispatcher;
import io.github.brickwall2900.processing.packets.processor.PacketProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProcessManagerChild extends ProcessManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessManagerChild.class);

    private final TLSCertificateFactory tlsCertificateFactory;
    private final PacketDispatcher<ProcessManagerChild, ProcessConnection> packetDispatcher;
    private final ChildMessenger messenger;
    private final ProcessConnection connectionToMaster;
    private final SynchronousQueue<Packet> packetSynchronousQueue = new SynchronousQueue<>();
    private final Set<UUID> siblings = ConcurrentHashMap.newKeySet();

    // nasty service stuff
    private NettyClientService clientService;

    // shutdown lock to prevent committing suicide while shutdown
    // AND it's also a determiner if the shutdown hook is running...
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private static TLSCertificateFactory createNewCertificateFactory(Path certificatePath, Path truststorePath) {
        // self-destruct on exit lol
        certificatePath.toFile().deleteOnExit();

        try {
            return new TLSCertificateFactory(
                    truststorePath,
                    certificatePath,
                    CERTIFICATE_NAME,
                    InetAddress.getLocalHost().getHostName(),
                    EXPIRE_DAYS
            );
        } catch (UnknownHostException e) {
            throw new UnknownError("failed to get hostname??");
        }
    }

    private void initPacketProcessors() {
        packetDispatcher.register(ShutdownRequestPacket.TYPE, new ShutdownRequestProcessor());
        packetDispatcher.register(MessagePacket.TYPE, messenger);
        packetDispatcher.register(SubscriptionStatusPacket.TYPE, messenger);
        ChildReplicationProcessor childReplicationProcessor = new ChildReplicationProcessor();
        packetDispatcher.register(ChildrenInitReplicationPacket.TYPE, childReplicationProcessor);
        packetDispatcher.register(ChildReplicationPacket.TYPE, childReplicationProcessor);
    }

    /// invoked as a child
    ProcessManagerChild(UUID myId, UUID parentId, Path certificatePath, Path truststorePath) {
        super(myId, parentId);
        LOGGER.debug("Initializing ProcessManager as child");

        this.tlsCertificateFactory = createNewCertificateFactory(certificatePath, truststorePath);
        this.packetDispatcher = new PacketDispatcher<>();
        this.messenger = new ChildMessenger(this);
        this.connectionToMaster = new ProcessConnection(parentId, null);

        initPacketProcessors();
    }

    @Override
    public boolean isConnected(UUID processId) {
        if (!Objects.equals(parentId, processId)) {
            return false;
        }

        return clientService.isRunning();
    }

    /// in the child instance's case, it'll return a copy of the replicated
    /// children tree the master gives.
    ///
    /// in that case, it'll return its siblings except itself but including the master process.
    @Override
    public @UnmodifiableView Set<UUID> getProcesses() {
        return Collections.unmodifiableSet(siblings);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Pk extends Packet,
            Pr extends ProcessManager,
            C extends ProcessConnection> void registerPacketProcessor(String type,
                                                                      PacketProcessor<Pk, Pr, C> processor) {
        packetDispatcher.register(type,
                (PacketProcessor<? extends Packet, ProcessManagerChild, ProcessConnection>) processor);
    }

    /// waits for the next packet to be read
    public @NotNull Packet waitForPacket() throws InterruptedException {
        return packetSynchronousQueue.take();
    }

    /// sends a packet to a child
    /// @throws NoSuchElementException if the process specified is not connected
    public void sendPacket(Packet packet) {
        Objects.requireNonNull(packet, "packet == null");

        executor.submit(() -> offThreadSendPacket(packet));
    }

    private void offThreadSendPacket(Packet packet) {
        connectionToMaster.sendPacket(packet);
    }

    @Override
    public Messenger getMessenger() {
        return messenger;
    }

    /// called by ProcessEntryPoint
    ///
    /// this connects the child process to the master process
    void connectToMaster(int port) throws IOException {
        if (clientService != null) {
            throw new IllegalStateException("Client already started");
        }

        LOGGER.debug("Connecting to master process at port {}", port);

        SslContext sslContext = null;
        if (USE_TLS) {
            char[] keyPassword = null;
            char[] caPassword = null;
            try {
                caPassword = passwordProvider.getTruststorePassword();
                keyPassword = passwordProvider.getKeyPassword(myId);

                LOGGER.debug("Creating client SSL context");
                sslContext = new SslContextBuilder()
                        .factory(tlsCertificateFactory)
                        .forClient()
                        .password(keyPassword)
                        .trustStorePassword(caPassword)
                        .build();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failure to start master", e);
            } finally {
                if (keyPassword != null) {
                    Arrays.fill(keyPassword, '\67');
                }
                if (caPassword != null) {
                    Arrays.fill(caPassword, '\67');
                }
            }
        }

        clientService = new NettyClientService(
                InetAddress.getLocalHost().getHostName(),
                port,
                sslContext,
                new ProcessChannelInboundHandler(this)
        );
        clientService.init();
        clientService.run();
        LOGGER.debug("Client connected to master");
    }

    /// called on either shutdown hook or the {@link ProcessManagerChild#exit(String, int)} method
    synchronized void onShutdownHook(String reason, int exitCode) {
        if (!shuttingDown.get()) {
            shuttingDown.set(true);
            LOGGER.debug("Child shutting down: {} ({})", reason, exitCode);

            if (clientService != null) {
                try {
                    ChannelFuture f;
                    if ((f = clientService.writeAndFlush(new ChildShutdownRequestPacket(reason, exitCode))) != null) {
                        f.sync();
                    }
                    LOGGER.debug("Shutdown request sent!");
                } catch (InterruptedException _) {
                }
            }

            try {
                LOGGER.debug("Now shutting down IPC connection");
                clientService.destroy();
            } catch (Exception _) {
            }

            try {
                LOGGER.debug("Deleting certificate file {}", tlsCertificateFactory.getKeystorePath());
                Files.deleteIfExists(tlsCertificateFactory.getKeystorePath());
            } catch (IOException _) {
            }
            LOGGER.debug("Bye!");

            // don't wanna call System.exit() here because mayb we're in a shutdown hook?
        }
    }

    /// Acknowledges the master that this child process will shut down and calls {@link System#exit(int)}
    public void exit(String reason, int exitCode) {
        onShutdownHook(reason, exitCode);

        System.exit(exitCode);
    }

    @Override
    void handlePacket(Channel channel, Packet packet) {
        packetSynchronousQueue.offer(packet);
        packetDispatcher.dispatch(packet, this, connectionToMaster, channel);
    }

    @Override
    void handleConnection(Channel channel, UUID client) {
        LOGGER.debug("Handling connection to master: {}", client);
        connectionToMaster.setChannel(channel);
        // if we are unencrypted, send a welcome packet
        // this will never happen on encrypted channels
        if (!USE_TLS) {
            connectionToMaster.sendPacket(new UnencryptedWelcomePacket(myId));
        }
    }

    @Override
    public void handleDisconnection(Channel channel) {
        if (!shuttingDown.get()) {
            LOGGER.warn("Lost connection to master!");
            Thread.ofPlatform().start(this::commitSuicide);
        }
    }

    /// this is ONLY used for emergencies such that the master can't be reached
    /// it just commits suicide...
    private void commitSuicide() {
        LOGGER.error("committing suicide!");
        Runtime.getRuntime().halt(0x2000);
    }

    @Override
    void onException(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("An exception occurred in channel!", cause);
        if (!shuttingDown.get()) {
            Thread.ofPlatform().start(this::commitSuicide);
        }
    }

    class ChildReplicationProcessor
            implements PacketProcessor<Packet, ProcessManagerChild, ProcessConnection> {

        @Override
        public void process(Packet packet,
                            ProcessManagerChild processor,
                            ProcessConnection connection,
                            Channel channel) {
            if (packet instanceof ChildrenInitReplicationPacket(Set<UUID> childrenProcesses)) {
                siblings.clear();
                siblings.addAll(childrenProcesses);
            } else if (packet instanceof ChildReplicationPacket(UUID processId, boolean added)) {
                if (added) {
                    siblings.add(processId);
                } else {
                    siblings.remove(processId);
                }
            }
        }
    }

    private static class ShutdownRequestProcessor implements
            PacketProcessor<ShutdownRequestPacket, ProcessManagerChild, ProcessConnection> {
        @Override
        public void process(ShutdownRequestPacket packet, ProcessManagerChild processor, ProcessConnection connection,
                            Channel channel) {
            connection.sendPacket(new ShutdownAckPacket());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException _) {
            }

            Thread.ofPlatform().start(() -> processor.exit("Shutdown by master request", packet.exitCode()));
        }
    }
}
