package Fuzzcode.utilities;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LoggerHandler {
    public enum Level {
        INFO,
        WARNING,
        ERROR
    }
    private LoggerHandler() {}

    private static final List<String> logs = new ArrayList<>();
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ISO_INSTANT;

    private static String format(Level level, String msg) {
        return TS_FORMAT.format(Instant.now()) + " [" + level + "] " + msg;
    }
    public static void log(String message) {
        logs.add(format(Level.INFO, message));
        System.out.println(format(Level.INFO, message));
    }
    public static void log(Level level, String message) {
        logs.add(format(level, message));
        System.out.println(format(Level.INFO, message));
    }
    public static void log(Exception e) {
        logs.add(format(Level.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage()));
        System.out.println(format(Level.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage()));
        for (StackTraceElement element : e.getStackTrace()) {
            logs.add("    at " + element.toString());
        }
    }
    public static void outputReport() {
        System.out.println("=== Logger Output ===");
        for (String log : logs) {
            System.out.println(log);
        }
        System.out.println("=====================");
    }
    public static void clear() {
        logs.clear();
    }

}
