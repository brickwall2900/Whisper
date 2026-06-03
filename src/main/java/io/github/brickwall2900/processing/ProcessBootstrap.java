package io.github.brickwall2900.processing;

import io.github.brickwall2900.processing.netty.NettyTypeThread;
import io.github.brickwall2900.processing.packets.ExceptionPacket;

import java.io.DataInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class ProcessBootstrap {
    private ProcessBootstrap() {
    }

    static void main(String[] args) {
        startShutdownThread();

        DataInputStream input = new DataInputStream(System.in);
        try {
            String entryPoint = input.readUTF();
            int masterPort = input.readInt();

            Path certificatePath = Path.of(input.readUTF());
            Path truststorePath = Path.of(input.readUTF());

            long uidMSB = input.readLong();
            long uidLSB = input.readLong();
            UUID myId = new UUID(uidMSB, uidLSB);

            uidMSB = input.readLong();
            uidLSB = input.readLong();
            UUID parentId = new UUID(uidMSB, uidLSB);

            boolean useTLS = input.readBoolean();
            String earlyBootRunnableClass = input.readUTF();

            System.setProperty("whisper.useTLS", String.valueOf(useTLS));
            System.setProperty("whisper.processId", String.valueOf(myId));

            ProcessManagerChild child = new ProcessManagerChild(myId, parentId, certificatePath, truststorePath);
            ProcessManager.instance = child;

            // set up early boot runnable
            setupEarlyBootRunnable(input,
                    child,
                    entryPoint,
                    masterPort,
                    useTLS,
                    earlyBootRunnableClass);

            child.connectToMaster(masterPort);

            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> child.onShutdownHook("Application Shutdown", 0)));

            tryExecutingMain(args, entryPoint);
        } catch (Throwable e) {
            errorReporting(e);
            throw new Error(e);
        }
    }

    private static void errorReporting(Throwable e) {
        try {
            ProcessManager processManager = ProcessManager.instance;

            // is there an existence of ProcessManager?
            if (processManager == null) {
                System.err.println("ERROR REPORTING FAILURE: ProcessManager not instantiated");
                return;
            }

            // does the parent ID exist?
            UUID parentId = processManager.parentId;
            if (parentId == null) {
                System.err.println("ERROR REPORTING FAILURE: parent UUID not initialized");
                return;
            }

            // does a password provider exist and is the connection encrypted?
            if (ProcessManager.USE_TLS && processManager.passwordProvider == null) {
                System.err.println("ERROR REPORTING FAILURE: PasswordProvider missing");
                return;
            }

            // are we actually connected?
            if (!processManager.isConnected(processManager.parentId)) {
                System.err.println("ERROR REPORTING FAILURE: not connected");
                return;
            }

            processManager.asChild().sendPacket(new ExceptionPacket(
                    ProcessManager.throwableToExceptionInfo(processManager.myId, e)));
        } catch (Exception _) {
        }
    }

    private static void setupEarlyBootRunnable(DataInputStream stdin,
                                               ProcessManagerChild child,
                                               String entrypointClass,
                                               int port,
                                               boolean useTLS,
                                               String earlyBootRunnableClassName) {
        try {
            Class<? extends EarlyBootRunnable> earlyBootRunnableClass = Class.forName(earlyBootRunnableClassName)
                    .asSubclass(EarlyBootRunnable.class);
            Constructor<?> constructor = findPublicNoArgConstructor(earlyBootRunnableClass);
            EarlyBootRunnable runnable = (EarlyBootRunnable) constructor.newInstance();
            runnable.boot(stdin, child, entrypointClass, port, useTLS);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to find the password provider class", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Error while calling target constructor", e);
        } catch (InstantiationException | ClassCastException e) {
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access constructor. " +
                    "Please make sure the provided early boot runnable exports its module to ProcessManager", e);
        } catch (Exception e) {
            throw new IllegalStateException("Exception thrown in early boot runnable", e);
        }
    }

    private static Constructor<?> findPublicNoArgConstructor(Class<?> cls) {
        Constructor<?> constructor;
        try {
            constructor = cls.getDeclaredConstructor();
        } catch (NoSuchMethodException m1) {
            try {
                constructor = cls.getConstructor();
            } catch (NoSuchMethodException m2) {
                m2.addSuppressed(m1);
                throw new IllegalStateException("No public no-arg constructor found", m2);
            }
        }
        return constructor;
    }

    private static void tryExecutingMain(String[] args, String entryPoint) {
        try {
            Class<?> targetClass = Class.forName(entryPoint);
            Method mainMethod = tryFindingMainMethod(targetClass);

            try {
                mainMethod.setAccessible(true);
            } catch (InaccessibleObjectException e) {
                System.err.println("WARNING: Bootstrap cannot make main method accessible");
            }

            if (mainMethod.getParameterCount() == 1) {
                mainMethod.invoke(null, (Object) args);
            } else {
                if (args != null && args.length > 0) {
                    System.err.println("WARNING: ProcessManager parent provided arguments " +
                            "but found main method with no String[]");
                }
                mainMethod.invoke(null);
            }
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            // the application could not be launched...
            throw new IllegalStateException("The application cannot be launched", e);
        }
    }

    private static void startShutdownThread() {
        ShutdownThread shutdownThread = new ShutdownThread();
        shutdownThread.start();
    }

    private static Method tryFindingMainMethod(Class<?> targetClass) {
        Method mainMethod;
        NoSuchMethodException error = null;
        try {
            mainMethod = targetClass.getMethod("main", String[].class);
            return mainMethod;
        } catch (NoSuchMethodException e) {
            if (error != null) {
                error.addSuppressed(e);
            }
            error = e;
        }

        try {
            mainMethod = targetClass.getMethod("main");
            return mainMethod;
        } catch (NoSuchMethodException e) {
            if (error != null) {
                error.addSuppressed(e);
            }
            error = e;
        }

        try {
            mainMethod = targetClass.getDeclaredMethod("main", String[].class);
            return mainMethod;
        } catch (NoSuchMethodException e) {
            if (error != null) {
                error.addSuppressed(e);
            }
            error = e;
        }

        try {
            mainMethod = targetClass.getDeclaredMethod("main");
            return mainMethod;
        } catch (NoSuchMethodException e) {
            if (error != null) {
                error.addSuppressed(e);
            }
            error = e;
        }

        throw new IllegalStateException("The main method sadly could not be found", error);
    }

    // this just looks for any non-daemon threads other than this thread and ProcessManager's threads
    // and if none are found, then shutdown time
    private static class ShutdownThread extends Thread {
        public ShutdownThread() {
            setName("ProcessManager shutdown thread");
            setPriority(Thread.MIN_PRIORITY);
            setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    int threadCount = Thread.activeCount();
                    Thread[] threads = new Thread[threadCount];
                    if (Thread.enumerate(threads) != threadCount) {
                        continue;
                    }

                    int nonDaemonThreads = countNonDaemonApplicationThreads(threads);

                    if (nonDaemonThreads <= 0) {
                        // it's shutdown time
                        ProcessManager.instance.asChild().exit("Application shutdown", 0);
                    }

                    Thread.sleep(Duration.ofSeconds(1));
                } catch (Exception _) {
                }
            }
        }

        private static int countNonDaemonApplicationThreads(Thread[] threads) {
            int nonDaemonThreads = 0;
            for (Thread thread : threads) {
                if (thread.isDaemon()) {
                    continue;
                }

                if (Objects.equals(Thread.currentThread(), thread)) {
                    continue;
                }

                if (thread instanceof NettyTypeThread) {
                    continue;
                }

                if (Objects.equals(thread.getName(), "DestroyJavaVM")) {
                    continue;
                }

                nonDaemonThreads++;
            }
            return nonDaemonThreads;
        }
    }
}
