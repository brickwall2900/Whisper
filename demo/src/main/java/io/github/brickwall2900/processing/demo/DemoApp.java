package io.github.brickwall2900.processing.demo;

import io.github.brickwall2900.processing.ProcessManager;
import io.github.brickwall2900.processing.ProcessManagerMaster;
import io.github.brickwall2900.processing.info.ChildProcessInfo;

public class DemoApp {
    static void main(String[] args) throws Exception {
        // hello!!

        // as you read by the README, Whisper facilitates interprocess communication in Java
        // in kind of, hopefully, a less painful way than plain sockets or whatever.

        // everything happens in ProcessManager
        // nothing in ProcessManager is initialized unless you call ProcessManager.getInstance()

        // so we get the instance of ProcessManager
        // we are the master process, not the child process, so we safely adapt it by using .asMaster()
        ProcessManagerMaster processManager = ProcessManager.getInstance().asMaster();

        // this process currently running right now is known the master process
        // and other processes we spawn ourselves are known as the child processes
        // we get to control children... you know, spawn them, kill them, and talk to them!

        // next step
        // set the password provider...
        // this is important !!
        // without a password provider, TLS encryption won't be set up!
        // technically, you could disable that using the system property `whisper.useTLS` = false

        // but we'll just keep TLS on hehe
        processManager.setPasswordProvider(new DemoPasswordProvider());

        try {
            // start the master server before doing anything else!
            // this lets child processes connect to us so we can communicate
            processManager.startMaster(6969);

            // now we execute a child application
            ProcessManagerMaster.ChildProcessResult result = processManager
                    .spawnChildProcess(new ChildProcessInfo()
                            .mainClass(DemoChildApp.class)
                            .earlyBootRunnableClass(DemoEarlyBoot.class)
                            .addArguments(args));
            // child process result gives us: the process UUID and its output stream

            // we close the process output stream, we don't need it now
            result.close();

            // now we wait for the child process to exit
            processManager.waitForProcessTermination(result.childProcessId());

            // ta-da! we're done!
            System.out.println("Child process finished executing");
        } finally {
            // finally, we stop the master server and clean up our resources
            processManager.stopMaster();
        }
    }
}
