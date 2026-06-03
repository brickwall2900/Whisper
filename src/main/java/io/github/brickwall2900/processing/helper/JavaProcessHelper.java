package io.github.brickwall2900.processing.helper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class JavaProcessHelper {
    private JavaProcessHelper() {}

    /**
     * Gets the currently running Java Virutal Machine's executable path
     *
     * @throws NullPointerException if JVM's executable path wasn't found
     * @return the path to the currently running JVM executable
     */
    public static Path getJavaVM(boolean hasConsole) {
        Path binPath = Path.of("bin");
        Path javaExecutable = binPath.resolve("java");

        // QUICK WINDOWS CHECK HAHA
        // so glad i stayed on linux btw
        if (System.getProperty("os.name")
                .toLowerCase()
                .contains("windows")) {
            javaExecutable = hasConsole
                    ? binPath.resolve("java.exe")
                    : binPath.resolve("javaw.exe");
        }

        // Method 1 :: System.getProperty("java.home")
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            Path javaPath = Path.of(javaHome);
            return javaPath.resolve(javaExecutable);
        }

        // Method 2 :: %JAVA_HOME%
        javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            Path javaPath = Path.of(javaHome);
            return javaPath.resolve(javaExecutable);
        }

        // Method 3 :: arguments?
        Optional<String[]> args = ProcessHandle.current().info().arguments();
        if (args.isPresent()) {
            Path javaPath = Path.of(args.get()[0]);
            return javaPath.resolve(javaExecutable);
        }

        // no JVM can be found. how did this run anyway?
        throw new NoSuchElementException("what kind of JVM is this?");
    }

    /**
     * @return Returns true if console exists by {@link System#console()}
     */
    public static boolean hasConsole() {
        return System.console() != null;
    }

    /**
     * Tries to find the main class containing the {@code public static void main(String[] args)} signature
     *
     * @throws NullPointerException if the main class wasn't found
     * @return the fully qualified class name for the main class
     */
    public static String getMainClass() {
        // Method 1 :: sun.java.command
        String command = System.getProperty("sun.java.command");
        if (command != null) {
            String[] javaArgs = command.split("\\w");
            String mainClass = javaArgs[0];
            boolean found;
            try {
                Class<?> cls = Class.forName(mainClass);
                Method signatureMethod;
                try {
                    signatureMethod = cls.getDeclaredMethod("main", String[].class);
                } catch (NoSuchMethodException m1) {
                    try {
                        signatureMethod = cls.getDeclaredMethod("main");
                    } catch (NoSuchMethodException m2) {
                        m2.addSuppressed(m1);
                        throw m2;
                    }
                }
                int modifiers = signatureMethod.getModifiers();
                found = Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers);
            } catch (ReflectiveOperationException e) {
                found = false;
            }
            if (found) {
                return mainClass;
            }
        }

        // Method 2 :: Digging through StackWalker
        StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        List<StackWalker.StackFrame> frames = stackWalker.walk(Stream::toList);
        String name = frames.getLast().getDeclaringClass().getName();

        // wait we're just straight up assuming we're in main thread?
        // what was bro writing YEARS ago ;-;
        if (!name.startsWith("java.lang.Thread")) {
            return name;
        }

        // we failed
        throw new NullPointerException("Main class not found!");
    }

    /**
     * Gets the JAR file of the input class file.
     *
     * @param cls the input class to find the JAR file
     * @throws URISyntaxException if URI somehow wasn't formatted correctly
     * @return the JAR file of the class file
     */
    public static Path getCodeSource(Class<?> cls) throws URISyntaxException {
        return Path.of(cls.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
    }

    /**
     * Creates the new arguments that can be used for relaunching this process
     *
     * @param moreArguments pass only if extra arguments are needed
     * @throws IllegalStateException if an {@link URISyntaxException} due to not parsing the JAR location throws?
     * @return a list of arguments
     */
    public static List<String> createArguments(List<String> moreArguments) {
        String determinedMainClass = getMainClass();
        String jarFile;
        try {
            jarFile = getCodeSource(JavaProcessHelper.class).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        List<String> cmdArgs = new ArrayList<>();

        cmdArgs.add("-cp");
        cmdArgs.add(System.getProperty("java.class.path", jarFile));
        cmdArgs.add(determinedMainClass);
        if (moreArguments != null) {
            cmdArgs.addAll(moreArguments);
        }
        return cmdArgs;
    }

    /**
     * @param prefix prefix of property to be included
     * @return a map of the properties starting with the prefix
     */
    // what the fff
    public static Map<String, String> getPropertiesStartingWith(String prefix) {
        Properties properties = System.getProperties();
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String name = (String) entry.getKey();
            if (name.startsWith(prefix)) {
                String value = (String) entry.getValue();
                map.put(name, value);
            }
        }
        return map;
    }

    /**
     * @param args input map of properties
     * @return arguments that have mapped properties into format {@code -Dkey=value}
     */
    // tf
    public static String[] mapPropertiesToArguments(Map<String, String> args) {
        String[] arguments = new String[args.size()];
        int idx = 0;
        for (Map.Entry<String, String> entry : args.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            arguments[idx] = String.format("-D%s=%s", key, val);
            idx++;
        }
        return arguments;
    }
}
