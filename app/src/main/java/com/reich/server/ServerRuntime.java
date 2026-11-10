package com.reich.server;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.os.Looper;

/**
 * Holds the current state of the game server. The foreground service updates
 * this state, the activity observes it.
 * <p>
 * Both live in the same process, so a simple static holder is enough and it
 * keeps the communication between the two trivial.
 */
public final class ServerRuntime {

    /**
     * The state of the server.
     */
    public enum State {
        /**
         * The server is not running.
         */
        STOPPED,
        /**
         * The server is loading definitions and binding the socket.
         */
        STARTING,
        /**
         * The server is online.
         */
        RUNNING,
        /**
         * The server crashed or could not be started.
         */
        ERROR
    }

    /**
     * A listener that is notified whenever the state changes.
     */
    public interface Listener {
        void onStateChanged(State state, String detail);
    }

    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile State state = State.STOPPED;
    private static volatile String detail = "The server is not running.";
    private static volatile int port = ServerPreferences.DEFAULT_PORT;

    private ServerRuntime() {
    }

    public static State getState() {
        return state;
    }

    public static String getDetail() {
        return detail;
    }

    public static int getPort() {
        return port;
    }

    public static boolean isRunning() {
        return state == State.RUNNING;
    }

    /**
     * Updates the state of the server and notifies every listener.
     *
     * @param newState the new state.
     * @param newDetail a human readable description of the state.
     */
    public static void setState(State newState, String newDetail) {
        state = newState;
        detail = newDetail;
        notifyListeners(newState, newDetail);
    }

    public static void setPort(int newPort) {
        port = newPort;
    }

    public static synchronized void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(State newState, String newDetail) {
        List<Listener> snapshot;
        synchronized (LISTENERS) {
            snapshot = new ArrayList<>(LISTENERS);
        }
        MAIN.post(() -> {
            for (Listener listener : snapshot) {
                try {
                    listener.onStateChanged(newState, newDetail);
                } catch (Throwable ignored) {
                    // A broken listener must never break the server.
                }
            }
        });
    }
}
