package io.github.brickwall2900.processing.demo;

import io.github.brickwall2900.processing.PasswordProvider;

import java.util.UUID;

// example password provider
// -------------------------
// truststore password must not change in a runtime of an application
// it's required for signing certificates and loading them whatnot...
//
// the key password can just be whatever honestly, idk...
public class DemoPasswordProvider implements PasswordProvider {
    // so this is the truststore password
    @Override
    public char[] getTruststorePassword() {
        return "Mari".toCharArray();
    }

    // this is the password use for the master
    @Override
    public char[] getKeyPassword() {
        return "Sunny".toCharArray();
    }

    // and this is the password for the child processes
    // notice the UUID parameter? that's for identifying child processes
    @Override
    public char[] getKeyPassword(UUID uuid) {
        return ("Basil" + uuid.toString()).toCharArray();
    }
}
