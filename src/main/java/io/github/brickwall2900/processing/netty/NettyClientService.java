package io.github.brickwall2900.processing.netty;

import io.github.brickwall2900.processing.packets.Packet;
import io.github.brickwall2900.processing.packets.PacketDecoder;
import io.github.brickwall2900.processing.packets.PacketEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Objects;

import static io.github.brickwall2900.processing.netty.NettyServerService.PACKET_MAX_SIZE;

public class NettyClientService implements Service {
    private static final int MAX_PORT = 65535;
    private final String hostname;
    private final int port;
    @Nullable
    private final SslContext sslContext;
    private final ChannelInboundHandler handler;

    private EventLoopGroup eventLoopGroup;
    private Bootstrap bootstrap;
    private ChannelFuture channelFuture;
    private Channel channel;

    public NettyClientService(String hostname,
                              int port,
                              @Nullable SslContext sslContext,
                              ChannelInboundHandler handler) {
        this.hostname = Objects.requireNonNull(hostname, "hostname == null");
        this.port = Objects.checkIndex(port, MAX_PORT);
        this.sslContext = sslContext;
        this.handler = Objects.requireNonNull(handler, "handler == null");
    }

    @Override
    public void init() {
        eventLoopGroup = new MultiThreadIoEventLoopGroup(NettyTypeThread::new, NioIoHandler.newFactory());
        bootstrap = new Bootstrap();
    }

    @Override
    public void run() {
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (sslContext != null) {
                            SslHandler sslHandler;
                            if (hostname != null) {
                                sslHandler = sslContext.newHandler(ch.alloc(), hostname, port);
                            } else {
                                sslHandler = sslContext.newHandler(ch.alloc());
                            }
                            ch.pipeline().addLast(sslHandler);
                        }
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(
                                        PACKET_MAX_SIZE,
                                        0,
                                        3,
                                        0,
                                        3))
                                .addLast(new PacketDecoder())
                                .addLast(new LengthFieldPrepender(3))
                                .addLast(new PacketEncoder())
                                .addLast(handler);
                    }
                });
        try {
            channelFuture = bootstrap.connect(new InetSocketAddress(hostname, port)).sync();
            channel = channelFuture.channel();
        } catch (InterruptedException ignored) {
        }
    }

    public void sync() throws InterruptedException {
        channelFuture.sync();
    }

    public boolean isRunning() {
        return channel != null;
    }

    public @Nullable ChannelFuture writeAndFlush(Packet packet) {
        if (channel != null) {
            return channel.writeAndFlush(packet);
        } else {
            // drop packet, maybe the server has shut down
            return null;
        }
    }

    @Override
    public void destroy() {
        try {
            if (channelFuture != null) {
                channel.close();
                channelFuture.sync()
                        .channel()
                        .closeFuture()
                        .addListener(ignored -> channel = null);
            }
        } catch (InterruptedException ignored) {
        }

        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
    }
}
