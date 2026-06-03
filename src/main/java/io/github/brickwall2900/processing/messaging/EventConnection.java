package io.github.brickwall2900.processing.messaging;

/// The `EventConnection` interface represents a handle to a connected event.
public interface EventConnection {
    /// checks if this listener is still connected to that event
    boolean isConnected();

    /// disconnect the listener from that event, such that it no longer receives events
    void disconnect();
}