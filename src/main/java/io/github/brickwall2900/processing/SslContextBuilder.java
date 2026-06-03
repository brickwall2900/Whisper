package io.github.brickwall2900.processing;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

final class SslContextBuilder {
    private TLSCertificateFactory factory;
    private boolean forServer;
    private char[] keyPassword;
    private char[] caPassword;

    public SslContextBuilder factory(TLSCertificateFactory factory) {
        this.factory = factory;
        return this;
    }

    public SslContextBuilder password(char[] password) {
        this.keyPassword = password;
        return this;
    }

    public SslContextBuilder trustStorePassword(char[] password) {
        this.caPassword = password;
        return this;
    }

    public SslContextBuilder forServer() {
        this.forServer = true;
        return this;
    }

    public SslContextBuilder forClient() {
        this.forServer = false;
        return this;
    }

    public SslContext build() throws IOException, UnrecoverableKeyException, CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        KeyManagerFactory keyManagerFactory = factory.getKeyStore(keyPassword);
        TrustManagerFactory trustManagerFactory = factory.getTrustManager(caPassword);

        // red light
        // green light
        return forServer
                ?
                io.netty.handler.ssl.SslContextBuilder
                .forServer(keyManagerFactory)
                .trustManager(trustManagerFactory)
                .clientAuth(ClientAuth.REQUIRE)
                .build()
                :
                io.netty.handler.ssl.SslContextBuilder
                .forClient()
                .keyManager(keyManagerFactory)
                .trustManager(trustManagerFactory)
                .build();
    }
}
