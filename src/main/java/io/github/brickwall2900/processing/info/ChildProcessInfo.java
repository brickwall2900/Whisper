package io.github.brickwall2900.processing.info;

import io.github.brickwall2900.processing.EarlyBootRunnable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildProcessInfo {
    private final List<Path> classpath = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private final Map<String, String> systemProperties = new HashMap<>();
    private Path workingDirectory;
    private String mainClass;
    private String earlyBootRunnable;

    public ChildProcessInfo addClassPath(Path file) {
        classpath.add(file);
        return this;
    }

    public ChildProcessInfo addArgument(String arg) {
        args.add(arg);
        return this;
    }

    public ChildProcessInfo addArguments(String... arg) {
        args.addAll(List.of(arg));
        return this;
    }

    public ChildProcessInfo mainClass(String mainClass) {
        this.mainClass = mainClass;
        return this;
    }

    public ChildProcessInfo mainClass(Class<?> mainClass) {
        this.mainClass = mainClass.getName();
        return this;
    }

    public ChildProcessInfo earlyBootRunnableClass(String earlyBootRunnableClass) {
        this.earlyBootRunnable = earlyBootRunnableClass;
        return this;
    }

    public ChildProcessInfo earlyBootRunnableClass(Class<? extends EarlyBootRunnable> earlyBootRunnableClass) {
        this.earlyBootRunnable = earlyBootRunnableClass.getName();
        return this;
    }

    public ChildProcessInfo workingDirectory(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    public ChildProcessInfo addSystemProperty(String key, String value) {
        this.systemProperties.put(key, value);
        return this;
    }

    public ChildProcessInfo addSystemProperties(Map<String, String> environment) {
        this.systemProperties.putAll(environment);
        return this;
    }

    public List<Path> getClasspath() {
        return classpath;
    }

    public List<String> getArguments() {
        return args;
    }

    public String getMainClass() {
        return mainClass;
    }

    public String getEarlyBootRunnable() {
        return earlyBootRunnable;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    @Override
    public String toString() {
        return "ChildProcessInfo{" +
                "classpath=" + classpath +
                ", args=" + args +
                ", systemProperties=" + systemProperties +
                ", workingDirectory=" + workingDirectory +
                ", mainClass='" + mainClass + '\'' +
                ", earlyBootRunnable='" + earlyBootRunnable + '\'' +
                '}';
    }
}
