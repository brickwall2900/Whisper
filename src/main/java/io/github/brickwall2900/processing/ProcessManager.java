package io.github.brickwall2900.processing;

import io.github.brickwall2900.processing.messaging.Messenger;
import io.github.brickwall2900.processing.packets.Packet;
import io.github.brickwall2900.processing.packets.processor.PacketProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class ProcessManager {
    static final boolean USE_TLS = Boolean.parseBoolean(System.getProperty("whisper.useTLS", "true"));
    static final Path IPC_CERTIFICATE_PATH = Path.of(
            System.getProperty("whisper.certificatePath", ".whisper"));
    static final Path TRUST_STORE_PATH = IPC_CERTIFICATE_PATH.resolve("ca.p12");
    static final String CERTIFICATE_NAME = "WhisperProcessManager";
    static final int EXPIRE_DAYS = 365 / 4;
    static ProcessManager instance;

    /// An executor service. Every packet processing MUST go here...
    protected final ExecutorService executor = Executors.newSingleThreadExecutor(ProcessManagerThread::new);

    protected final UUID myId;
    protected final UUID parentId;

    protected PasswordProvider passwordProvider;

    private ProcessManagerMaster asMasterProcessManager;

    protected ProcessManager(UUID myId, UUID parentId) {
        this.myId = myId;
        this.parentId = parentId;
    }

    /// Only used internally between {@link ProcessBootstrap} and {@link ProcessManagerMaster}
    static ExceptionInfo throwableToExceptionInfo(UUID uuid, Throwable t) {
        if (t == null) {
            return null;
        }
        return new ExceptionInfo(
                uuid,
                t.getClass().getName(),
                t.getMessage(),
                Arrays.stream(t.getStackTrace()).map(StackTraceElement::toString).toArray(String[]::new),
                throwableToExceptionInfo(uuid, t.getCause())
        );
    }

    /// sets the password provider of this instance's ProcessManager
    /// @see PasswordProvider#getKeyPassword()
    public void setPasswordProvider(PasswordProvider passwordProvider) {
        this.passwordProvider = passwordProvider;
    }

    /// @return the Messenger to communicate between processes
    public abstract Messenger getMessenger();

    /// @return the current UUID of this process
    public UUID getMyId() {
        return myId;
    }

    /// @return parent UUID if this process belongs to a child,
    /// {@code null} if this process is the master itself.
    public @Nullable UUID getParentId() {
        return parentId;
    }

    /// @return differentiates whether this is a child process or not
    /// @implNote this implementation checks if this instance is an instance of {@link ProcessManagerChild}
    public boolean isChild() {
        return this instanceof ProcessManagerChild;
    }

    /// @return {@code true} if and only if the process is connected to this master instance
    /// @implSpec it should return {@code true} if the parameter {@code processId} is referring to itself
    public abstract boolean isConnected(UUID processId);

    /// @return an immutable set of processes that are alive and connected to this master.
    public abstract @UnmodifiableView Set<UUID> getProcesses();

    /// registers a {@link PacketProcessor} to read and process packets.
    /// @throws IllegalArgumentException if such packet processor for the given type is already registered.
    public abstract <Pk extends Packet,
            Pr extends ProcessManager,
            C extends ProcessConnection> void registerPacketProcessor(String type,
                                                                      PacketProcessor<Pk, Pr, C> processor);

    /// @return an instance of ProcessManager initialized as the master.
    /// returns this instance if the current instance is a master instance.
    public ProcessManagerMaster asMaster() {
        if (this instanceof ProcessManagerMaster master) {
            return master;
        } else if (asMasterProcessManager == null) {
            asMasterProcessManager = new ProcessManagerMaster(myId);
        }

        return asMasterProcessManager;
    }

    /// @return an instance of ProcessManager as the child.
    /// @throws ClassCastException if this instance of ProcessManager is not a child
    public ProcessManagerChild asChild() {
        if (this instanceof ProcessManagerChild child) {
            return child;
        } else {
            throw new ClassCastException("This ProcessManager instance is not a child");
        }
    }

    public static ProcessManager getInstance() {
        if (instance == null) {
            instance = new ProcessManagerMaster(null);
        }

        return instance;
    }

    /// called from {@link ProcessChannelInboundHandler} to handle packets
    abstract void handlePacket(Channel channel, Packet msg);

    /// called from {@link ProcessChannelInboundHandler} to handle connection
    abstract void handleConnection(Channel channel, UUID client);

    /// called from {@link ProcessChannelInboundHandler} to handle disconnection
    abstract void handleDisconnection(Channel channel);

    /// called from {@link ProcessChannelInboundHandler} to handle exceptions
    abstract void onException(ChannelHandlerContext ctx, Throwable cause);
}
