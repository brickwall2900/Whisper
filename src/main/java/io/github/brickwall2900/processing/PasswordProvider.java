package io.github.brickwall2900.processing;

import java.util.UUID;

public interface PasswordProvider {
    /// provides a password for truststore certificate
    ///
    /// @apiNote password will be automatically cleared by ProcessManager
    /// @return password in char[] array
    char[] getTruststorePassword();

    /// provides a password for keystore certificate
    ///
    /// @apiNote password will be automatically cleared by ProcessManager
    /// @return password in char[] array
    char[] getKeyPassword();

    /// provides a password for keystore certificate
    ///
    /// @apiNote password will be automatically cleared by ProcessManager
    /// @return password in char[] array
    char[] getKeyPassword(UUID uuid);
}
