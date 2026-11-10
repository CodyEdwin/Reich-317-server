package com.reich.server;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Bridges the server's logging ({@link java.util.logging} and
 * {@code System.out}/{@code System.err}) into the in-app console.
 * <p>
 * The server code is a plain Java project that logs to the standard output and
 * to {@code java.util.logging}. This class captures both of them so the user
 * can see what the server is doing without having to attach logcat.
 */
public final class ServerLog {

    /**
     * The tag used for every logcat entry written by the server.
     */
    public static final String TAG = "ReichServer";

    /**
     * The maximum amount of lines kept in memory.
     */
    private static final int MAX_LINES = 1000;

    /**
     * A single line of the server console.
     */
    public static final class Entry {
        private final long timestamp = System.currentTimeMillis();
        private final Level level;
        private final String message;

        Entry(Level level, String message) {
            this.level = level;
            this.message = message;
        }

        public Level getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String format() {
            String time = android.text.format.DateFormat.format("HH:mm:ss", timestamp).toString();
            return time + "  " + message;
        }
    }

    /**
     * A listener that is notified whenever a new line is logged.
     */
    public interface Listener {
        void onLog(Entry entry, List<Entry> history);
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final android.os.Handler MAIN = new android.os.Handler(Looper.getMainLooper());
    private static boolean installed;

    private ServerLog() {
    }

    /**
     * Installs the log bridge. This redirects {@code System.out} and
     * {@code System.err} and adds a handler to the root logger.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        Logger root = Logger.getLogger("");
        root.addHandler(new LogcatHandler());
        root.setLevel(Level.ALL);

        try {
            System.setOut(new PrintStream(new TeeStream(System.out), true, "UTF-8"));
            System.setErr(new PrintStream(new TeeStream(System.err), true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unable to redirect the standard streams", e);
        }
    }

    /**
     * Logs a message to the console.
     *
     * @param level   the severity of the message.
     * @param message the message.
     */
    public static void log(Level level, String message) {
        Entry entry = new Entry(level, message);
        synchronized (ENTRIES) {
            ENTRIES.addLast(entry);
            while (ENTRIES.size() > MAX_LINES) {
                ENTRIES.removeFirst();
            }
        }
        notifyListeners(entry);
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARNING, message);
    }

    public static void error(String message, Throwable throwable) {
        String text = message;
        if (throwable != null) {
            text = message + " (" + throwable + ")";
        }
        log(Level.SEVERE, text);
    }

    /**
     * @return a snapshot of every logged line.
     */
    public static List<Entry> getHistory() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES);
        }
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }

    public static synchronized void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(Entry entry) {
        List<Listener> snapshot;
        synchronized (LISTENERS) {
            snapshot = new ArrayList<>(LISTENERS);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        List<Entry> history = getHistory();
        MAIN.post(() -> {
            for (Listener listener : snapshot) {
                try {
                    listener.onLog(entry, history);
                } catch (Throwable ignored) {
                    // A broken listener must never break the server.
                }
            }
        });
    }

    /**
     * A {@link java.util.logging.Handler} that forwards every log record to the
     * in-app console.
     */
    private static final class LogcatHandler extends java.util.logging.Handler {

        @Override
        public void publish(LogRecord record) {
            if (record == null) {
                return;
            }
            String message = record.getMessage();
            if (record.getParameters() != null && record.getParameters().length > 0) {
                try {
                    message = java.text.MessageFormat.format(message, record.getParameters());
                } catch (Throwable ignored) {
                    // Keep the raw message.
                }
            }
            if (record.getThrown() != null) {
                message = message + " (" + record.getThrown() + ")";
            }
            log(record.getLevel(), message);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    /**
     * A stream that forwards everything written to it to another stream and
     * to the in-app console, line by line.
     */
    private static final class TeeStream extends OutputStream {

        private final OutputStream delegate;
        private final StringBuilder buffer = new StringBuilder();

        TeeStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws java.io.IOException {
            delegate.write(b);
            if (b == '\n') {
                flushLine();
            } else if (b != '\r') {
                synchronized (buffer) {
                    buffer.append((char) b);
                }
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws java.io.IOException {
            delegate.write(b, off, len);
            synchronized (buffer) {
                for (int i = off; i < off + len; i++) {
                    byte value = b[i];
                    if (value == '\n') {
                        flushLine();
                    } else if (value != '\r') {
                        buffer.append((char) value);
                    }
                }
            }
        }

        @Override
        public void flush() throws java.io.IOException {
            delegate.flush();
        }

        private void flushLine() {
            String line;
            synchronized (buffer) {
                line = buffer.toString();
                buffer.setLength(0);
            }
            if (!line.isEmpty()) {
                log(Level.INFO, line);
            }
        }
    }
}
