package io.github.brickwall2900.processing;

import java.util.UUID;

/// This interface listens to child process shutdowns
@FunctionalInterface
public interface ChildShutdownHandler {
    void onChildShutdown(UUID processId, String reason, int exitCode);
}
