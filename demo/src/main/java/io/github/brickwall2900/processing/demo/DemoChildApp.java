package io.github.brickwall2900.processing.demo;

import io.github.brickwall2900.processing.ProcessManager;
import io.github.brickwall2900.processing.ProcessManagerChild;

import java.text.ListFormat;
import java.util.Arrays;

public class DemoChildApp {
    // unless our module `opens` to the processing module,
    // the bootstrap cannot find our main method so we must make it public
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[] { "Albert", "Einstein" };
        }

        // child application level logic here
        System.out.println("Hello, " + ListFormat.getInstance().format(Arrays.asList(args)));

        // because we are in a child process, we can get the ProcessManager kids mode
        ProcessManagerChild processManager = ProcessManager.getInstance().asChild();

        // we can be a master, but we won't do that here
        // processManager.asMaster().startMaster(14367);

        // exit this application and notify the master
        processManager.exit("we are done babyyy", 67);
    }
}
