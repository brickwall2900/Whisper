package io.github.brickwall2900.processing;

import io.github.brickwall2900.processing.packets.Packet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.UUID;

@ChannelHandler.Sharable
final class ProcessChannelInboundHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessChannelInboundHandler.class);
    private final ProcessManager processManager;

    public ProcessChannelInboundHandler(ProcessManager processManager) {
        this.processManager = processManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) {
        Channel channel = ctx.channel();
        processManager.executor.submit(() -> offThreadChannelRead(channel, msg));
    }

    private void offThreadChannelRead(Channel channel, Packet msg) {
        processManager.handlePacket(channel, msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof SslHandshakeCompletionEvent event) {
            Channel channel = ctx.channel();
            if (processManager.isChild()) {
                processManager.executor.submit(() ->
                        offThreadChannelHandshakeActive(channel, processManager.parentId));
                return;
            }

            if (event.isSuccess()) {
                LOGGER.info("TLS handshake success!");

                SslHandler handler = ctx.pipeline().get(SslHandler.class);
                Certificate[] peerCertificates;

                try {
                    peerCertificates = handler.engine().getSession().getPeerCertificates();
                } catch (SSLPeerUnverifiedException e) {
                    ctx.close();
                    throw new IllegalStateException("Unable to verify SSL certificate, closing!", e);
                }

                if (peerCertificates.length > 0 && peerCertificates[0] instanceof X509Certificate clientCertificate) {
                    String principalName = clientCertificate.getSubjectX500Principal().getName();
                    UUID client = UUID.fromString(principalName.replace("CN=", ""));

                    if (processManager.asMaster().expectToConnect(client)) {
                        processManager.executor.submit(() ->
                                offThreadChannelHandshakeActive(channel, client));
                    } else {
                        ctx.close();
                        throw new IllegalStateException("A non-existent client ID logged in," +
                                " what is this?! " + client);
                    }
                }
            } else {
                LOGGER.error("TLS handshake failed, closing!", event.cause());
                ctx.close();
            }
        }
    }

    private void offThreadChannelHandshakeActive(Channel channel, UUID client) {
        processManager.handleConnection(channel, client);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // only in special case where TLS is off, we don't do a handshake so we js do it here
        // what do we do exactly?
        // we do a fucking handshake at the protocol level haha
        if (!ProcessManager.USE_TLS && processManager.isChild()) {
            Channel channel = ctx.channel();
            processManager.executor.submit(() -> offThreadChannelActive(channel));
        }
    }

    private void offThreadChannelActive(Channel channel) {
        processManager.handleConnection(channel, processManager.parentId);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        processManager.executor.submit(() -> offThreadChannelDisconnect(channel));
    }

    private void offThreadChannelDisconnect(Channel channel) {
        processManager.handleDisconnection(channel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        processManager.onException(ctx, cause);
    }
}
