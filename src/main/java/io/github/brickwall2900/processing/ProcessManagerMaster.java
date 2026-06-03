package io.github.brickwall2900.processing;

import io.github.brickwall2900.processing.helper.JavaProcessCreator;
import io.github.brickwall2900.processing.info.ChildProcessInfo;
import io.github.brickwall2900.processing.messaging.Messenger;
import io.github.brickwall2900.processing.messaging.impl.MasterMessenger;
import io.github.brickwall2900.processing.netty.NettyServerService;
import io.github.brickwall2900.processing.packets.*;
import io.github.brickwall2900.processing.packets.processor.PacketDispatcher;
import io.github.brickwall2900.processing.packets.processor.PacketProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.SynchronousQueue;

public final class ProcessManagerMaster extends ProcessManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessManagerMaster.class);
    private static final AttributeKey<ProcessConnection> SESSION_KEY =
            AttributeKey.valueOf("session");

    private final TLSCertificateFactory tlsCertificateFactory;
    private final Map<UUID, ProcessConnection> processConnections;
    private final PacketDispatcher<ProcessManagerMaster, ProcessConnection> packetDispatcher;
    private final Set<ProcessConnection> shutdownAcknowledged = ConcurrentHashMap.newKeySet();
    private final Deque<ExceptionInfo> exceptionDeque = new ConcurrentLinkedDeque<>();
    private final MasterMessenger messenger;
    private final Set<UUID> processesShuttingDown = ConcurrentHashMap.newKeySet();
    private final SynchronousQueue<Packet> packetSynchronousQueue = new SynchronousQueue<>();

    // nasty service stuff
    private NettyServerService serverService;

    private ChildShutdownHandler childShutdownHandler;

    private static TLSCertificateFactory createNewCertificateFactory(Path certificatePath) {
        try {
            return new TLSCertificateFactory(
                    TRUST_STORE_PATH,
                    certificatePath,
                    CERTIFICATE_NAME,
                    InetAddress.getLocalHost().getHostName(),
                    EXPIRE_DAYS
            );
        } catch (UnknownHostException e) {
            throw new UnknownError("Failed to get host name??");
        }
    }

    private void initPacketProcessors() {
        packetDispatcher.register(ShutdownAckPacket.TYPE, new ShutdownAckProcessor());
        packetDispatcher.register(UnencryptedWelcomePacket.TYPE, new UnencryptedWelcomeProcessor());
        packetDispatcher.register(MessagePacket.TYPE, messenger);
        packetDispatcher.register(SubscriptionStatusPacket.TYPE, messenger);
        packetDispatcher.register(ChildShutdownRequestPacket.TYPE, new ChildShutdownRequestProcessor());
        packetDispatcher.register(ExceptionPacket.TYPE, new ExceptionProcessor());
    }

    /// invoked as a master
    /// @param uuid UUID to assign the master, if {@code null}, then will assign a random UUID
    ProcessManagerMaster(@Nullable UUID uuid) {
        super(uuid != null ? uuid : UUID.randomUUID(), null);
        LOGGER.debug("Initializing ProcessManager as master");

        this.tlsCertificateFactory = createNewCertificateFactory(IPC_CERTIFICATE_PATH.resolve("master.p12"));
        this.processConnections = new ConcurrentHashMap<>();
        this.packetDispatcher = new PacketDispatcher<>();
        this.messenger = new MasterMessenger(this);

        initPacketProcessors();
    }

    private ProcessBuilder createProcess(ChildProcessInfo info) {
        ProcessBuilder builder = new JavaProcessCreator()
                .addToClassPath(info.getClasspath().stream()
                        .map(Path::toString)
                        .toArray(String[]::new))
                .addAppArguments(info.getArguments().toArray(String[]::new))
                .entryPoint(ProcessBootstrap.class)
                // okay so the reason why we set it there is that ProcessEntryPoint will handle
                // the entry point for us and will enter it automatically
                // ProcessEntryPoint is the one setting up the environent for child processes
                .addJVMArguments(info.getSystemProperties()
                        .entrySet()
                        .stream()
                        .map(entry -> Map.entry("-D" + entry.getKey(), entry.getValue()))
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new))
                .stripJVMAgent()
                .build()
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        if (info.getWorkingDirectory() != null) {
            builder.directory(info.getWorkingDirectory().toFile());
        }
        LOGGER.debug("Command: {}", builder.command());
        return builder;
    }

    /// spawns a child processes under master.
    ///
    /// UUID of the child process is assigned by this method.
    ///
    /// {@link ProcessManagerMaster#startMaster(int)} must be called first!
    /// @implNote it creates certificates
    /// @implNote a password provider must be set up for the child to connect to the master through TLS.
    /// if TLS is disabled, password provider may be null.
    /// @param info child process info
    /// @throws IOException if an I/O error occurs within starting the process itself
    /// @return UUID of the child process
    public ChildProcessResult spawnChildProcess(ChildProcessInfo info) throws IOException {
        Objects.requireNonNull(serverService, "Did you forget to start the master server?");
        Objects.requireNonNull(info, "info == null");
        Objects.requireNonNull(info.getMainClass(), "main class == null");
        Objects.requireNonNull(info.getEarlyBootRunnable(), "early boot class == null");

        LOGGER.debug("Child process creation: {}", info);

        int port = serverService.getPort();
        UUID childId = UUID.randomUUID();

        String certificateName = String.valueOf(new SecureRandom().nextLong());
        Path certificatePath = IPC_CERTIFICATE_PATH.resolve(certificateName + ".p12");

        LOGGER.debug("UUID of child process is {}", childId);

        if (USE_TLS) {
            LOGGER.debug("Creating certificates for child");
            char[] keyPassword = null;
            char[] caPassword = null;
            try {
                TLSCertificateFactory tlsCertificateFactory = createNewCertificateFactory(certificatePath);
                caPassword = passwordProvider.getTruststorePassword();
                keyPassword = passwordProvider.getKeyPassword(childId);
                tlsCertificateFactory.createCertificates(caPassword, keyPassword, childId.toString(), false);
            } finally {
                if (keyPassword != null) {
                    Arrays.fill(keyPassword, '\67');
                }
                if (caPassword != null) {
                    Arrays.fill(caPassword, '\67');
                }
            }
        }

        ProcessBuilder builder = createProcess(info);
        Process process = builder.start();
        LOGGER.debug("Process ID {} has spawned", process.toHandle().pid());

        processConnections.put(childId, new ProcessConnection(childId, process));

        DataOutputStream output = new DataOutputStream(process.getOutputStream());
        // entrypoint
        output.writeUTF(info.getMainClass());

        // server address
        // output.writeUTF(serverService.getAddress().getHostAddress());
        // it hardcodes itself to the local address...
        // will it be a good idea if we implement remote process management?

        // port
        output.writeInt(port);

        // certificate path
        output.writeUTF(certificatePath.toString());
        // truststore path
        output.writeUTF(TRUST_STORE_PATH.toString());

        // your own child id
        output.writeLong(childId.getMostSignificantBits());
        output.writeLong(childId.getLeastSignificantBits());
        // my own child id (as master)
        output.writeLong(myId.getMostSignificantBits());
        output.writeLong(myId.getLeastSignificantBits());

        output.writeBoolean(USE_TLS);
        // class path to early boot runnable
        output.writeUTF(info.getEarlyBootRunnable());

        output.flush();

        // connection will happen once ProcessEntryPoint sets up the environment
        // and connects to the master process (which is me)

        return new ChildProcessResult(childId, output);
    }

    /// This is the return value of {@link ProcessManagerMaster#spawnChildProcess(ChildProcessInfo)}
    ///
    /// The `stream` parameter must be closed manually when no longer needed!
    public record ChildProcessResult(UUID childProcessId, DataOutputStream stream) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    /// starts the server used to connect child processes
    ///
    /// does nothing if the server already started
    ///
    /// a password provider must be set before calling this method!
    /// @see ProcessManagerMaster#setPasswordProvider(PasswordProvider)
    /// @implNote it creates certificates
    /// @throws IOException if an I/O failure occurs
    /// @throws IllegalStateException if any security failure occurs within {@link SslContext} init occurs
    /// @param port port to expose for child processes to connect
    public void startMaster(int port) throws IOException {
        Objects.requireNonNull(passwordProvider, "No password provider set");
        if (serverService != null) {
            return;
        }

        SslContext sslContext = null;
        if (USE_TLS) {
            char[] caPassword = null;
            char[] keyPassword = null;
            try {
                caPassword = passwordProvider.getTruststorePassword();
                keyPassword = passwordProvider.getKeyPassword();

                LOGGER.debug("Creating certificates");
                Files.createDirectories(IPC_CERTIFICATE_PATH);
                tlsCertificateFactory.createCertificateAuthority(caPassword);
                tlsCertificateFactory.createCertificates(caPassword, keyPassword, myId.toString(), true);

                LOGGER.debug("Creating SSL context");
                sslContext = new SslContextBuilder()
                        .factory(tlsCertificateFactory)
                        .forServer()
                        .password(keyPassword)
                        .trustStorePassword(caPassword)
                        .build();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failure to start master", e);
            } finally {
                if (caPassword != null) {
                    Arrays.fill(caPassword, '\67');
                }
                if (keyPassword != null) {
                    Arrays.fill(keyPassword, '\67');
                }
            }
        }

        try {
            LOGGER.debug("Starting server");
            InetAddress localhost = InetAddress.getLocalHost();
            serverService = new NettyServerService(
                    localhost,
                    localhost.getHostName(),
                    port,
                    sslContext,
                    new ProcessChannelInboundHandler(this)
            );
            serverService.init();
            serverService.run();
        } catch (UnknownHostException e) {
            throw new IOException("Unable to get localhost", e);
        }
        LOGGER.debug("Master server started at port {}", port);
    }

    /// stops accepting connections and stops the master server itself
    ///
    /// it closes all connections and shuts down and wait for all child processes!
    ///
    /// does nothing if the server is already stopped
    ///
    /// the master server can be restarted again
    public void stopMaster() throws InterruptedException {
        if (serverService != null) {
            if (!processConnections.isEmpty()) {
                shutdownAllProcesses();
            }

            LOGGER.debug("Stopping server!");

            serverService.destroy();
            serverService = null;
        }
    }

    private void shutdownAllProcesses() throws InterruptedException {
        LOGGER.debug("Closing all connections");
        Set<UUID> waitForProcesses = new HashSet<>();
        for (UUID processId : processConnections.keySet()) {
            LOGGER.debug("Shutting down process {}", processId);
            if (isConnected(processId)) {
                if (!shutdownProcess(processId, 0, Duration.ofSeconds(5))) {
                    LOGGER.debug("Forcefully terminating process {} after shutdown time exceeded", processId);
                    forceTerminate(processId);
                } else {
                    waitForProcesses.add(processId);
                }
            }
        }
        for (UUID waitingFor : waitForProcesses) {
            waitForProcessTermination(waitingFor);
        }
    }

    /// requests the child to cleanly shut down,
    /// blocks until shut down time is reached.
    ///
    /// @throws NoSuchElementException if the process isn't connected to the master
    ///
    /// @param processId process child ID to shut down
    /// @param exitCode optional exit code to close the child process
    /// @param shutdownTime shut down timer for the process. the duration in which the process
    /// will shut down will determine how long this method will block
    ///
    /// @return {@code true} if the child process acknowledged the shutdown, {@code false} if otherwise
    public boolean shutdownProcess(UUID processId, int exitCode, Duration shutdownTime) {
        Objects.requireNonNull(processId, "processId == null");
        Objects.requireNonNull(shutdownTime, "shutdownTime == null");

        ProcessConnection connection = processConnections.get(processId);
        if (connection == null || !connection.isConnected()) {
            throw new NoSuchElementException("Process isn't connected to master");
        }

        LOGGER.info("Requesting graceful shutdown of {}", processId);
        connection.sendPacket(new ShutdownRequestPacket(exitCode));

        synchronized (connection) {
            try {
                connection.wait(shutdownTime.toMillis());
            } catch (InterruptedException _) {
                LOGGER.debug("interrupted during shutdown wait time");
            }
        }

        if (shutdownAcknowledged.remove(connection)) {
            LOGGER.debug("Process {} acknowledged shutdown", processId);
            return true;
        }

        LOGGER.debug("Process {} did NOT acknowledge shutdown", processId);
        return false;
    }

    /// waits for the process to terminate.
    /// does nothing if this process isn't connected to the master anymore
    public void waitForProcessTermination(UUID processId, Duration initWaitTime) throws InterruptedException {
        Objects.requireNonNull(processId, "processId == null");
        Objects.requireNonNull(initWaitTime, "initWaitTime == null");

        ProcessConnection connection = processConnections.get(processId);
        if (connection == null) {
            return;
        }

        Process process = connection.getProcess();
        if (process != null) {
            process.waitFor(initWaitTime);
        }
    }

    /// waits for the process to terminate indefinitely.
    /// does nothing if this process isn't connected to the master anymore
    public void waitForProcessTermination(UUID processId) throws InterruptedException {
        Objects.requireNonNull(processId, "processId == null");

        ProcessConnection connection = processConnections.get(processId);
        if (connection == null) {
            return;
        }

        Process process = connection.getProcess();
        if (process != null) {
            process.waitFor();
        }
    }

    /// forcefully kill the child
    ///
    /// does absolutely nothing if the child isn't controlled by the master or if this child is dead
    /// @param waitDuration time to wait until {@link Process#destroyForcibly()} is called,
    /// if and only if the child is still somehow alive
    public void forceTerminate(UUID processId, Duration waitDuration) {
        Objects.requireNonNull(processId, "processId == null");
        Objects.requireNonNull(waitDuration, "waitDuration == null");

        ProcessConnection connection = processConnections.get(processId);
        if (connection == null) {
            return;
        }

        processesShuttingDown.add(processId);

        Process process = connection.getProcess();
        if (process != null && process.isAlive()) {
            process.destroy();

            try {
                if (!process.waitFor(waitDuration)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException _) {
                process.destroyForcibly();
            }
        }
    }

    /// kills the child. {@link Process#destroyForcibly()} isn't called.
    ///
    /// does absolutely nothing if the child isn't controlled by the master or if this child is dead
    public void forceTerminate(UUID processId) {
        Objects.requireNonNull(processId, "processId == null");

        ProcessConnection connection = processConnections.get(processId);
        if (connection == null) {
            return;
        }

        Process process = connection.getProcess();
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    /// poll for any exceptions that may have happened
    /// while handling connections on child processes
    ///
    /// @return an exception if there is one, `null` otherwise
    public @Nullable ExceptionInfo pollExceptions() {
        return exceptionDeque.poll();
    }

    @Override
    public boolean isConnected(UUID processId) {
        if (Objects.equals(myId, processId)) {
            return true;
        }

        return processConnections.containsKey(processId)
                && processConnections.get(processId).isConnected();
    }

    boolean expectToConnect(UUID processId) {
        return processConnections.containsKey(processId);
    }

    /// waits for the connection on the specified proces ID indefinitely
    ///
    /// does nothing if already connected
    ///
    /// @throws NoSuchElementException if the process isn't even connected
    public void waitForConnection(UUID processId) {
        ProcessConnection connection = processConnections.get(processId);
        if (connection == null) {
            throw new NoSuchElementException("Process isn't connected");
        }

        if (connection.getChannel() != null) {
            return;
        }

        synchronized (connection) {
            try {
                connection.wait();
            } catch (InterruptedException _) {
            }
        }
    }

    /// waits for the connection on the specified proces ID
    ///
    /// does nothing if already connected
    ///
    /// @throws NoSuchElementException if the process isn't even connected
    public void waitForConnection(UUID processId, Duration duration) {
        ProcessConnection connection = processConnections.get(processId);
        if (connection == null) {
            throw new NoSuchElementException("Process isn't connected");
        }

        if (connection.getChannel() != null) {
            return;
        }

        synchronized (connection) {
            try {
                connection.wait(duration.toMillis(), duration.toNanosPart());
            } catch (InterruptedException _) {
            }
        }
    }

    @Override
    public @UnmodifiableView Set<UUID> getProcesses() {
        return Collections.unmodifiableSet(processConnections.keySet());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Pk extends Packet,
            Pr extends ProcessManager,
            C extends ProcessConnection> void registerPacketProcessor(String type,
                                                                      PacketProcessor<Pk, Pr, C> processor) {
        packetDispatcher.register(type,
                (PacketProcessor<? extends Packet, ProcessManagerMaster, ProcessConnection>) processor);
    }

    /// sends a packet to a child
    /// @throws NoSuchElementException if the process specified is not connected
    public void sendPacket(UUID processId, Packet packet) {
        Objects.requireNonNull(packet, "packet == null");

        ProcessConnection connection = processConnections.get(processId);
        if (connection == null) {
            throw new NoSuchElementException("Process is not connected to this parent");
        }

        executor.submit(() -> offThreadSendPacket(connection, packet));
    }

    private void offThreadSendPacket(ProcessConnection connection, Packet packet) {
        connection.sendPacket(packet);
    }

    @Override
    public Messenger getMessenger() {
        return messenger;
    }

    /// sets and listens for child processes shutdown
    /// @see ChildShutdownHandler
    public void setChildShutdownHandler(ChildShutdownHandler childShutdownHandler) {
        this.childShutdownHandler = childShutdownHandler;
    }

    /// waits for the next packet to be read
    public @NotNull Packet waitForPacket() throws InterruptedException {
        return packetSynchronousQueue.take();
    }

    @Override
    void handlePacket(Channel channel, Packet packet) {
        packetSynchronousQueue.offer(packet);

        ProcessConnection connection = channel.attr(SESSION_KEY).get();
        if (connection == null) {
            LOGGER.warn("Unknown channel attempting to connect to ProcessManager; address: {}",
                    channel.remoteAddress());
        }
        packetDispatcher.dispatch(packet, this, connection, channel);
    }

    @Override
    void handleConnection(Channel channel, UUID client) {
        LOGGER.info("Welcome, {}!", client);
        ProcessConnection connection = processConnections.get(client);
        connection.setChannel(channel);

        synchronized (connection) {
            connection.notifyAll();
        }

        channel.attr(SESSION_KEY).set(connection);

        // send child replication packet
        connection.sendPacket(new ChildrenInitReplicationPacket(getInitReplicationProcesses(client)));

        // and signal to other processes that this child process or whatever has started
        ChildReplicationPacket replicationPacket = new ChildReplicationPacket(client, true);
        for (UUID otherProcess : getProcesses()) {
            if (!Objects.equals(otherProcess, client)) {
                sendPacket(otherProcess, replicationPacket);
            }
        }
    }

    /// gets the set of processes that will be replicated onto the new child process
    private Set<UUID> getInitReplicationProcesses(UUID newChildProcess) {
        Set<UUID> processes = new HashSet<>(getProcesses());
        processes.add(myId);
        processes.remove(newChildProcess);
        return processes;
    }

    @Override
    public void handleDisconnection(Channel channel) {
        ProcessConnection connection = channel.attr(SESSION_KEY).get();
        if (connection != null) {
            UUID processId = connection.getUUID();

            processConnections.remove(processId);

            LOGGER.debug("{} has disconnected, bye!", processId);

            // signal to other processes that this child process or whatever is shutting odwn
            ChildReplicationPacket replicationPacket = new ChildReplicationPacket(processId, false);
            for (UUID otherProcess : getProcesses()) {
                sendPacket(otherProcess, replicationPacket);
            }

            // is the process already shutting down?
            // remove it and move on
            if (processesShuttingDown.remove(processId)) {
                return;
            }
            LOGGER.debug("Lost connection to child {}", processId);
            Process process = connection.getProcess();
            if (process.isAlive()) {
                LOGGER.debug("Child process is still alive, forcefully terminating");
                forceTerminate(processId, Duration.ofSeconds(5));
            }
        }
    }

    @Override
    void onException(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("An exception occurred in channel handling!", cause);
        Channel channel = ctx.channel();
        ProcessConnection connection = channel.attr(SESSION_KEY).get();
        if (connection != null
                && !processesShuttingDown.contains(connection.getUUID())
                && connection.getProcess() != null) {
            forceTerminate(connection.getUUID());
            exceptionDeque.offer(ProcessManager.throwableToExceptionInfo(connection.getUUID(), cause));
        }
    }

    private static class ShutdownAckProcessor implements
            PacketProcessor<ShutdownAckPacket, ProcessManagerMaster, ProcessConnection> {

        @Override
        public void process(ShutdownAckPacket packet, ProcessManagerMaster processor, ProcessConnection connection,
                            Channel channel) {
            processor.shutdownAcknowledged.add(connection);
            synchronized (connection) {
                // what in the actual fuck?
                connection.notifyAll();
            }
        }
    }

    private static class UnencryptedWelcomeProcessor implements
            PacketProcessor<UnencryptedWelcomePacket, ProcessManagerMaster, ProcessConnection> {
        @Override
        public void process(UnencryptedWelcomePacket packet,
                            ProcessManagerMaster processor,
                            ProcessConnection connection,
                            Channel channel) {
            if (USE_TLS) {
                throw new UnsupportedOperationException("This shouldn't even be possible," +
                        " did a child process manipulate its system properties?");
            }

            if (connection != null || channel.attr(SESSION_KEY).get() != null) {
                throw new IllegalStateException("Connection already signed in");
            }

            LOGGER.debug("Logging in unencrypted: {}", packet.processId());

            if (processor.expectToConnect(packet.processId())) {
                processor.handleConnection(channel, packet.processId());
            }
        }
    }

    private static class ChildShutdownRequestProcessor
            implements PacketProcessor<ChildShutdownRequestPacket, ProcessManagerMaster, ProcessConnection> {
        @Override
        public void process(ChildShutdownRequestPacket packet,
                            ProcessManagerMaster processor,
                            ProcessConnection connection, Channel channel) {
            if (connection != null) {
                UUID processId = connection.getUUID();
                processor.processesShuttingDown.add(processId);
                if (processor.childShutdownHandler != null) {
                    processor.childShutdownHandler.onChildShutdown(processId, packet.reason(), packet.exitCode());
                }
            }
        }
    }

    private static class ExceptionProcessor
            implements PacketProcessor<ExceptionPacket, ProcessManagerMaster, ProcessConnection> {
        @Override
        public void process(ExceptionPacket packet,
                            ProcessManagerMaster processor,
                            ProcessConnection connection, Channel channel) {
            processor.exceptionDeque.offer(packet.info());
        }
    }
}
