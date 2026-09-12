package singleplayer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

/**
 * Verbose single-player startup trace used to diagnose Android boot stalls.
 *
 * The JVM is launched with the single-player world directory as user.dir, so the
 * trace is intentionally written there. That makes it directly available from
 * the launcher's World Files button without requiring adb or Android/data access.
 */
public final class SinglePlayerDebug {
    private static final long HEARTBEAT_MS = 5_000L;
    private static final long THREAD_DUMP_MS = 20_000L;

    private static final long PROCESS_START_MS = System.currentTimeMillis();
    private static final Object INSTALL_LOCK = new Object();

    private static volatile boolean installed;
    private static volatile boolean watchdogRunning;
    private static volatile String stage = "bootstrap not started";
    private static volatile long stageStartedMs = PROCESS_START_MS;
    private static volatile Thread watchdog;

    private SinglePlayerDebug() {}

    public static void install() {
        if (installed) return;
        synchronized (INSTALL_LOCK) {
            if (installed) return;

            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            try {
                Path root = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
                Path current = root.resolve("singleplayer-debug.log");
                Path previous = root.resolve("singleplayer-debug.previous.log");
                if (Files.exists(current)) {
                    Files.move(current, previous, StandardCopyOption.REPLACE_EXISTING);
                }

                FileOutputStream file = new FileOutputStream(current.toFile(), false);
                System.setOut(new PrintStream(
                        new TeeOutputStream(originalOut, file), true, "UTF-8"));
                System.setErr(new PrintStream(
                        new TeeOutputStream(originalErr, file), true, "UTF-8"));
                installed = true;

                Thread.UncaughtExceptionHandler prior =
                        Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
                    failure("UNCAUGHT " + thread.getName(), failure);
                    if (prior != null) {
                        try {
                            prior.uncaughtException(thread, failure);
                        } catch (Throwable ignored) {
                            // Diagnostic logging must never hide the original crash.
                        }
                    }
                });

                log("SESSION", "============================================================");
                log("SESSION", "single-player diagnostic session " + Instant.now());
                log("SESSION", "debugFile=" + current);
                log("SESSION", "java=" + System.getProperty("java.version")
                        + " vendor=" + System.getProperty("java.vendor"));
                log("SESSION", "os=" + System.getProperty("os.name") + " "
                        + System.getProperty("os.version") + " arch="
                        + System.getProperty("os.arch"));
                log("SESSION", "user.dir=" + System.getProperty("user.dir"));
                log("SESSION", "clientHomeOverride="
                        + System.getProperty("clientHomeOverride", ""));
                log("SESSION", "processors=" + Runtime.getRuntime().availableProcessors());
                logMemory("SESSION");

                // Full phone builds now always arm the startup watchdog. This is
                // intentionally part of the normal bootstrap rather than a
                // separate diagnostic flavor: if startup ever stalls again, the
                // same log contains the thread state instead of forcing another
                // blind rebuild. The watchdog self-terminates once the client is
                // playable, failed, or stopped.
                startWatchdog();
            } catch (Throwable failure) {
                originalErr.println("SINGLEPLAYER_DEBUG: unable to create debug log: " + failure);
                failure.printStackTrace(originalErr);
            }
        }
    }

    public static void startWatchdog() {
        install();
        if (watchdogRunning) return;
        synchronized (INSTALL_LOCK) {
            if (watchdogRunning) return;
            watchdogRunning = true;
            watchdog = new Thread(SinglePlayerDebug::watchdogLoop,
                    "singleplayer-startup-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            log("WATCHDOG", "startup watchdog started");
        }
    }

    public static void stopWatchdog() {
        if (!watchdogRunning) return;
        watchdogRunning = false;
        Thread running = watchdog;
        if (running != null && running != Thread.currentThread()) running.interrupt();
        log("WATCHDOG", "startup watchdog stopped at stage=" + stage);
        logMemory("WATCHDOG");
    }

    public static void stage(String value) {
        if (value == null) value = "<null>";
        stage = value;
        stageStartedMs = System.currentTimeMillis();
        log("STAGE", value);
    }

    public static void log(String category, String message) {
        install();
        long now = System.currentTimeMillis();
        long elapsed = now - PROCESS_START_MS;
        String thread = Thread.currentThread().getName();
        System.out.println("SINGLEPLAYER_DEBUG [" + elapsed + "ms] [" + thread + "] ["
                + category + "] " + message);
    }

    public static void logDuration(String category, String operation, long startedNs) {
        long elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L;
        log(category, operation + " completed in " + elapsedMs + "ms");
    }

    public static void failure(String category, Throwable failure) {
        install();
        log(category, "FAILURE " + failure.getClass().getName() + ": "
                + String.valueOf(failure.getMessage()));
        failure.printStackTrace(System.err);
        System.err.flush();
    }

    private static void watchdogLoop() {
        long lastDumpMs = PROCESS_START_MS;
        while (watchdogRunning) {
            try {
                Thread.sleep(HEARTBEAT_MS);
            } catch (InterruptedException ignored) {
                if (!watchdogRunning) return;
            }
            if (!watchdogRunning) return;

            LocalGameRuntime.State runtimeState = LocalGameRuntime.get().state();
            if (runtimeState == LocalGameRuntime.State.RUNNING
                    || runtimeState == LocalGameRuntime.State.PAUSED
                    || runtimeState == LocalGameRuntime.State.FAILED
                    || runtimeState == LocalGameRuntime.State.STOPPED) {
                watchdogRunning = false;
                log("WATCHDOG", "startup complete; state=" + runtimeState
                        + "; watchdog exiting");
                return;
            }

            long now = System.currentTimeMillis();
            log("WATCHDOG", "alive; runtimeState=" + runtimeState
                    + "; stage=" + stage + "; stageAgeMs="
                    + (now - stageStartedMs));
            logMemory("WATCHDOG");

            if (now - lastDumpMs >= THREAD_DUMP_MS) {
                dumpThreads();
                lastDumpMs = now;
            }
        }
    }

    private static void logMemory(String category) {
        Runtime runtime = Runtime.getRuntime();
        long mb = 1024L * 1024L;
        log(category, "memoryMiB used=" + ((runtime.totalMemory() - runtime.freeMemory()) / mb)
                + " total=" + (runtime.totalMemory() / mb)
                + " max=" + (runtime.maxMemory() / mb));
    }

    private static void dumpThreads() {
        try {
            Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
            log("THREADS", "BEGIN thread dump; threads=" + traces.size());
            traces.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                    .forEach(entry -> {
                        Thread thread = entry.getKey();
                        System.out.println("--- THREAD " + thread.getName()
                                + " id=" + thread.getId()
                                + " state=" + thread.getState()
                                + " daemon=" + thread.isDaemon());
                        for (StackTraceElement frame : entry.getValue()) {
                            System.out.println("    at " + frame);
                        }
                    });
            log("THREADS", "END thread dump");
        } catch (Throwable failure) {
            failure("THREADS", failure);
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream primary;
        private final OutputStream file;

        TeeOutputStream(OutputStream primary, OutputStream file) {
            this.primary = primary;
            this.file = file;
        }

        @Override
        public void write(int value) throws IOException {
            primary.write(value);
            synchronized (file) {
                file.write(value);
            }
        }

        @Override
        public void write(byte[] bytes, int off, int len) throws IOException {
            primary.write(bytes, off, len);
            synchronized (file) {
                file.write(bytes, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            primary.flush();
            synchronized (file) {
                file.flush();
            }
        }
    }
}
