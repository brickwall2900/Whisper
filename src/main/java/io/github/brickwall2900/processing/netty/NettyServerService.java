package io.github.brickwall2900.processing.netty;

import io.github.brickwall2900.processing.packets.PacketDecoder;
import io.github.brickwall2900.processing.packets.PacketEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;

public class NettyServerService implements Service {
    private static final int MAX_PORT = 65535;
    public static final int PACKET_MAX_SIZE = (1024 * 1024 * 16) - 1;
    private final InetAddress address;
    private final String hostname;
    private final int port;
    @Nullable
    private final SslContext sslContext;
    private final ChannelInboundHandler handler;

    private EventLoopGroup eventLoopGroup;
    private ServerBootstrap serverBootstrap;
    private ChannelFuture channelFuture;

    public NettyServerService(InetAddress address,
                              String hostname,
                              int port,
                              @Nullable SslContext sslContext,
                              ChannelInboundHandler handler) {
        this.address = Objects.requireNonNull(address, "address == null");
        this.hostname = Objects.requireNonNull(hostname, "hostname == null");
        this.port = Objects.checkIndex(port, MAX_PORT);
        this.sslContext = sslContext;
        this.handler = Objects.requireNonNull(handler, "handler == null");
    }

    @Override
    public void init() {
        eventLoopGroup = new MultiThreadIoEventLoopGroup(NettyTypeThread::new, NioIoHandler.newFactory());
        serverBootstrap = new ServerBootstrap();
    }

    @Override
    public void run() {
        channelFuture = serverBootstrap.group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
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
                })
                .bind(new InetSocketAddress(address, port));
    }

    public void sync() throws InterruptedException {
        channelFuture.sync();
    }

    public void waitForClose() throws InterruptedException {
        channelFuture.channel().closeFuture().sync();
    }

    public int getPort() {
        return port;
    }

    public InetAddress getAddress() {
        return address;
    }

    @Override
    public void destroy() {
        try {
            if (channelFuture != null) {
                channelFuture.channel().close();
                channelFuture.sync()
                        .channel()
                        .closeFuture()
                        .sync();
            }
        } catch (InterruptedException ignored) {
        }

        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
    }
}
