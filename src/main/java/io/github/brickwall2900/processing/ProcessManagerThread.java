package io.github.brickwall2900.processing;

/// just a special thread ProcessManager uses
final class ProcessManagerThread extends Thread {
    public ProcessManagerThread(Runnable runnable) {
        super(runnable);
        setName("ProcessManagerMainThread");
        setPriority(NORM_PRIORITY);
        setDaemon(true);
    }
}
