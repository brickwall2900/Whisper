package io.github.brickwall2900.processing.netty;

import java.util.concurrent.atomic.AtomicInteger;

// a custom class to identify a thread that belongs to netty
public class NettyTypeThread extends Thread {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    public NettyTypeThread(Runnable r) {
        super(r);
        setName("WhisperNettyThread-" + THREAD_COUNTER.getAndIncrement());
        setPriority(MAX_PRIORITY);
    }
}
