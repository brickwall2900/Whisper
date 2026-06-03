package io.github.brickwall2900.processing.demo.chat;

import io.github.brickwall2900.processing.EarlyBootRunnable;
import io.github.brickwall2900.processing.ProcessManagerChild;

import java.io.DataInputStream;

public class ChatEarlyBoot implements EarlyBootRunnable {
    @Override
    public void boot(DataInputStream stdin, ProcessManagerChild child, String entrypointClass, int port, boolean useTLS) {
        child.setPasswordProvider(new ChatPasswordProvider());
    }
}
