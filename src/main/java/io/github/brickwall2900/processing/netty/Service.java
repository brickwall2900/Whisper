package io.github.brickwall2900.processing.netty;

public interface Service {
    void init();
    void run();
    void destroy();
}
