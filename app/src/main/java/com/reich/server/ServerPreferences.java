package com.reich.server;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Holds the user preferences of the application (port, wake lock, auto start,
 * ...). The preferences are shared between the UI and the foreground service.
 */
public final class ServerPreferences {

    private static final String FILE_NAME = "reich_server_prefs";

    private static final String KEY_PORT = "port";
    private static final String KEY_WAKE_LOCK = "wake_lock";
    private static final String KEY_WIFI_LOCK = "wifi_lock";
    private static final String KEY_START_ON_BOOT = "start_on_boot";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_RUNNING = "running";

    /**
     * The default port the game server binds to.
     */
    public static final int DEFAULT_PORT = 43595;

    private final SharedPreferences preferences;

    public ServerPreferences(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public int getPort() {
        return preferences.getInt(KEY_PORT, DEFAULT_PORT);
    }

    public void setPort(int port) {
        preferences.edit().putInt(KEY_PORT, port).apply();
    }

    public boolean isWakeLockEnabled() {
        return preferences.getBoolean(KEY_WAKE_LOCK, true);
    }

    public void setWakeLockEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_WAKE_LOCK, enabled).apply();
    }

    public boolean isWifiLockEnabled() {
        return preferences.getBoolean(KEY_WIFI_LOCK, true);
    }

    public void setWifiLockEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_WIFI_LOCK, enabled).apply();
    }

    public boolean isStartOnBootEnabled() {
        return preferences.getBoolean(KEY_START_ON_BOOT, false);
    }

    public void setStartOnBootEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_START_ON_BOOT, enabled).apply();
    }

    public boolean isAutoStartEnabled() {
        return preferences.getBoolean(KEY_AUTO_START, false);
    }

    public void setAutoStartEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_START, enabled).apply();
    }

    /**
     * Whether the server was running the last time the application was alive.
     * This is used to restore the server after the process was killed.
     */
    public boolean wasRunning() {
        return preferences.getBoolean(KEY_RUNNING, false);
    }

    public void setRunning(boolean running) {
        preferences.edit().putBoolean(KEY_RUNNING, running).apply();
    }

    @Nullable
    public SharedPreferences getPreferences() {
        return preferences;
    }
}
