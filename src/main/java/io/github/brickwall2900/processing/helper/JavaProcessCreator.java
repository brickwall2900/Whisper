package io.github.brickwall2900.processing.helper;

import java.io.File;
import java.lang.management.RuntimeMXBean;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaProcessCreator {
    public ProcessBuilder build() {
        Path jvm = javaVirtualMachineExecutable == null
                ? JavaProcessHelper.getJavaVM(JavaProcessHelper.hasConsole())
                : javaVirtualMachineExecutable;

        ProcessBuilder processBuilder = new ProcessBuilder();

        String determinedMainClass = entryPoint == null ? JavaProcessHelper.getMainClass() : entryPoint;

        RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
        List<String> runtimeArgs = runtime.getInputArguments();
        List<String> cmdArgs = new ArrayList<>();

        if (!stripJVMArguments) {
            cmdArgs.addAll(runtimeArgs);
        }

        if (stripJVMAgent) {
            cmdArgs.removeIf(s -> s.contains("-agentlib:") || s.contains("-javaagent:"));
        }

        cmdArgs.addAll(jvmArgs);
        cmdArgs.add("-cp");
        char separator = File.pathSeparatorChar;
        cmdArgs.add(classpath.stream()
                .reduce("", (x, y) -> x + separator + y));
        cmdArgs.add(determinedMainClass);
        cmdArgs.addAll(appArgs);

        List<String> pbCommand = new ArrayList<>();
        pbCommand.add(jvm.toString());
        pbCommand.addAll(cmdArgs);
        processBuilder.command(pbCommand);
        return processBuilder;
    }

    public JavaProcessCreator() {
        Path jarFile;
        try {
            jarFile = JavaProcessHelper.getCodeSource(JavaProcessCreator.class);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        String classpath = System.getProperty("java.class.path", jarFile.toString());
        String pathSeparator = File.pathSeparator;
        String[] split = classpath.split(pathSeparator);

        this.classpath.addAll(Arrays.asList(split));
    }

    public JavaProcessCreator jvmExecutable(Path jvmExecutablePath) {
        this.javaVirtualMachineExecutable = jvmExecutablePath;
        return this;
    }

    public JavaProcessCreator entryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
        return this;
    }

    public JavaProcessCreator entryPoint(Class<?> entryPoint) {
        this.entryPoint = entryPoint.getName();
        return this;
    }

    public JavaProcessCreator setJVMArguments(List<String> args) {
        this.jvmArgs.clear();
        return addJVMArguments(args);
    }

    public JavaProcessCreator addJVMArguments(List<String> args) {
        this.jvmArgs.addAll(args);
        return this;
    }

    public JavaProcessCreator addJVMArguments(String... args) {
        this.addJVMArguments(Arrays.asList(args));
        return this;
    }

    public JavaProcessCreator addJVMArgument(String arg) {
        this.jvmArgs.add(arg);
        return this;
    }

    public JavaProcessCreator setAppArguments(List<String> args) {
        this.appArgs.clear();
        return addJVMArguments(args);
    }

    public JavaProcessCreator addAppArguments(List<String> args) {
        this.appArgs.addAll(args);
        return this;
    }

    public JavaProcessCreator addAppArguments(String... args) {
        this.addAppArguments(Arrays.asList(args));
        return this;
    }

    public JavaProcessCreator addAppArgument(String arg) {
        this.appArgs.add(arg);
        return this;
    }

    public JavaProcessCreator stripJVMArgs() {
        this.stripJVMArguments = true;
        return this;
    }

    public JavaProcessCreator stripJVMAgent() {
        this.stripJVMAgent = true;
        return this;
    }

    public JavaProcessCreator addToClassPath(String classPath) {
        this.classpath.add(classPath);
        return this;
    }

    public JavaProcessCreator addToClassPath(String[] classPath) {
        this.classpath.addAll(Arrays.asList(classPath));
        return this;
    }

    public JavaProcessCreator removeFromClassPath(String classPath) {
        this.classpath.remove(classPath);
        return this;
    }

    private final List<String> jvmArgs = new ArrayList<>();
    private final List<String> appArgs = new ArrayList<>();

    private final List<String> classpath = new ArrayList<>();

    private boolean stripJVMArguments, stripJVMAgent;

    private Path javaVirtualMachineExecutable;
    private String entryPoint;
}
