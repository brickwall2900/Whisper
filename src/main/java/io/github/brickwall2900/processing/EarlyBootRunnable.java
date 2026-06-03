package io.github.brickwall2900.processing;

import java.io.DataInputStream;

/// The `EarlyBootRunnable` class sets up things (like setting up the {@link PasswordProvider})
/// before connecting to the master process.
///
/// This runs in the child process bootstrap after it has read information,
/// and before connecting to the master process.
///
/// @implSpec The implementing class must have a public no-argument constructor to be instantiated from the
/// child side.
public interface EarlyBootRunnable {
    void boot(DataInputStream stdin,
              ProcessManagerChild child,
              String entrypointClass,
              int port,
              boolean useTLS);
}
