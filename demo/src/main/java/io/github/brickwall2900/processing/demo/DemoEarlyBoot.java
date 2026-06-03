package io.github.brickwall2900.processing.demo;

import io.github.brickwall2900.processing.EarlyBootRunnable;
import io.github.brickwall2900.processing.ProcessManagerChild;

import java.io.DataInputStream;

// this is the code that will be run before it even connects to the master process
// it's essential since you can set the password provider and do other bootstrap stuff here
public class DemoEarlyBoot implements EarlyBootRunnable {
    @Override
    public void boot(DataInputStream stdin,
                     ProcessManagerChild child,
                     String entrypointClass,
                     int port,
                     boolean useTLS) {
        child.setPasswordProvider(new DemoPasswordProvider());

        // we are here:
        // [x] main method invocation
        // [x] critical info received from System.in
        // [x] ProcessManager instantiation
        // [ ] early boot runnable <--- WE ARE HERE
        // [ ] connect to master process
        // [ ] app main method
    }
}
