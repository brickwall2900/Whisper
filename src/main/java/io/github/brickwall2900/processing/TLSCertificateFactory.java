package io.github.brickwall2900.processing;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.DestroyFailedException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

final class TLSCertificateFactory {
    private static final boolean REGENERATE_CERTIFICATES = true;
    private static final String KEY_ALGORITHM = "EC";
    private static final String STORE_TYPE = "PKCS12";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final Path caKeystore;
    private final Path keystore;

    private final String certificateName;
    private final String fixedHostName;
    private final int expireDays;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public TLSCertificateFactory(Path caKeystore,
                                 Path keystore,
                                 String certificateName,
                                 String fixedHostName,
                                 int expireDays) {
        this.caKeystore = caKeystore;
        this.keystore = keystore;
        this.certificateName = certificateName;
        this.fixedHostName = fixedHostName;
        this.expireDays = expireDays;
    }

    public void createCertificateAuthority(char[] password) {
        KeyPair caKeyPair = null;
        try {
            if (Files.exists(caKeystore) && !REGENERATE_CERTIFICATES) {
                return;
            }

            caKeyPair = generateKeyPair();
            X509Certificate caCertificate = generateCaCertificate(
                    caKeyPair,
                    "CN=" + certificateName + "-CA"
            );

            saveKeyStore(
                    caKeystore,
                    "ca",
                    caKeyPair.getPrivate(),
                    password,
                    caCertificate
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate CA certificate!", e);
        } finally {
            if (caKeyPair != null) {
                try {
                    caKeyPair.getPrivate().destroy();
                } catch (DestroyFailedException ignored) {
                }
            }
        }
    }

    private PrivateKey generateCaPrivateKey(char[] password) throws IOException, KeyStoreException,
            CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore store = KeyStore.getInstance(STORE_TYPE);
        try (InputStream stream = openTrustStore()) {
            store.load(stream, password);
        }
        return (PrivateKey) store.getKey("ca", password);
    }

    private X509Certificate readCaCertificate(char[] password) throws KeyStoreException, IOException,
            CertificateException, NoSuchAlgorithmException {
        KeyStore store = KeyStore.getInstance(STORE_TYPE);
        try (InputStream stream = openTrustStore()) {
            store.load(stream, password);
        }
        return (X509Certificate) store.getCertificate("ca");
    }

    public void createCertificates(char[] truststorePassword,
                                   char[] password,
                                   String masterName,
                                   boolean server) {
        PrivateKey caPrivateKey = null;
        try {
            if (Files.exists(keystore) && !REGENERATE_CERTIFICATES) {
                return;
            }

            if (Files.notExists(caKeystore)) {
                throw new IllegalStateException("Did you forget to create a certificate authority?");
            }

            caPrivateKey = generateCaPrivateKey(truststorePassword);
            X509Certificate caCertificate = readCaCertificate(truststorePassword);

            KeyPair masterKeyPair = generateKeyPair();
            X509Certificate masterCertificate = generateSignedCertificate(
                    masterKeyPair,
                    "CN=" + masterName,
                    caPrivateKey,
                    caCertificate,
                    server
            );

            saveKeyStore(
                    keystore,
                    masterName,
                    masterKeyPair.getPrivate(),
                    password,
                    masterCertificate,
                    caCertificate
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate TLS certificates </3", e);
        } finally {
            if (caPrivateKey != null) {
                try {
                    caPrivateKey.destroy();
                } catch (DestroyFailedException ignored) {
                }
            }
        }
    }

    public X509Certificate readCertificate(char[] password)  throws KeyStoreException, IOException,
                CertificateException, NoSuchAlgorithmException {
        KeyStore store = KeyStore.getInstance(STORE_TYPE);
        try (InputStream stream = openKeyStore()) {
            store.load(stream, password);
        }
        Enumeration<String> aliases = store.aliases();
        Iterator<String> aliasIterator = aliases.asIterator();
        assert aliasIterator.hasNext();
        String alias = aliasIterator.next();
        return (X509Certificate) store.getCertificate(alias);
    }

    public InputStream openKeyStore() {
        try {
            return Files.newInputStream(keystore);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public InputStream openTrustStore() {
        try {
            return Files.newInputStream(caKeystore);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path getKeystorePath() {
        return keystore;
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private X509Certificate generateCaCertificate(
            KeyPair keyPair,
            String subject
    ) throws Exception {
        Instant now = Instant.now();

        X500Name name = new X500Name(subject);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                randomSerial(),
                Date.from(now.minus(1, ChronoUnit.MINUTES)),
                Date.from(now.plus(expireDays, ChronoUnit.DAYS)),
                name,
                keyPair.getPublic()
        );

        builder.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(true)
        );

        builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)
        );

        builder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keyPair.getPublic())
        );

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(keyPair.getPrivate());

        X509CertificateHolder holder = builder.build(signer);

        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private X509Certificate generateSignedCertificate(
            KeyPair keyPair,
            String subject,
            PrivateKey caPrivateKey,
            X509Certificate caCertificate,
            boolean server
    ) throws Exception {
        Instant now = Instant.now();

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caCertificate,
                randomSerial(),
                Date.from(now.minus(1, ChronoUnit.MINUTES)),
                Date.from(now.plus(expireDays, ChronoUnit.DAYS)),
                new X500Name(subject),
                keyPair.getPublic()
        );

        builder.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(false)
        );

        int usage = KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
        if (server) {
            usage |= KeyUsage.keyAgreement;
        }

        builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(usage)
        );

        builder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keyPair.getPublic())
        );

        builder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(caCertificate)
        );

        List<GeneralName> hosts = new ArrayList<>();
        hosts.add(new GeneralName(GeneralName.dNSName, "localhost"));
        hosts.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));
        if (fixedHostName != null) {
            hosts.add(new GeneralName(GeneralName.dNSName, fixedHostName));
        }

        builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                new GeneralNames(hosts.toArray(GeneralName[]::new))
        );

        builder.addExtension(
                Extension.extendedKeyUsage,
                false,
                new ExtendedKeyUsage(server ? KeyPurposeId.id_kp_serverAuth : KeyPurposeId.id_kp_clientAuth)
        );

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(caPrivateKey);

        X509CertificateHolder holder = builder.build(signer);

        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);

        certificate.verify(caCertificate.getPublicKey());
        return certificate;
    }

    private void saveKeyStore(
            Path path,
            String alias,
            PrivateKey privateKey,
            char[] password,
            X509Certificate... chain
    ) throws Exception {
        KeyStore store = KeyStore.getInstance(STORE_TYPE);
        store.load(null, password);

        store.setKeyEntry(alias, privateKey, password, chain);

        try (OutputStream output = Files.newOutputStream(path)) {
            store.store(output, password);
        }
    }

    private BigInteger randomSerial() {
        return new BigInteger(128, new SecureRandom()).abs();
    }

    /**
     * @implNote stream closes after method call
     */
    public KeyManagerFactory getKeyStore(char[] password) throws KeyStoreException, IOException,
            CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = openKeyStore()) {
            keyStore.load(input, password);
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
        );
        keyManagerFactory.init(keyStore, password);
        return keyManagerFactory;
    }

    /**
     * @implNote stream closes after method call
     */
    public TrustManagerFactory getTrustManager(char[] password) throws KeyStoreException, IOException,
            CertificateException, NoSuchAlgorithmException {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = openTrustStore()) {
            trustStore.load(input, password);
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        trustManagerFactory.init(trustStore);
        return trustManagerFactory;
    }
}
