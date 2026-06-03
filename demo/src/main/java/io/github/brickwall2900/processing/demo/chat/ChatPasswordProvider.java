package io.github.brickwall2900.processing.demo.chat;

import io.github.brickwall2900.processing.PasswordProvider;

import java.util.UUID;

// realistically, this should be hidden
public class ChatPasswordProvider implements PasswordProvider {
    @Override
    public char[] getTruststorePassword() {
        return "grizzly bear".toCharArray();
    }

    @Override
    public char[] getKeyPassword() {
        return "panda bear".toCharArray();
    }

    @Override
    public char[] getKeyPassword(UUID uuid) {
        return ("polar bear" + uuid.toString()).toCharArray();
    }
}
