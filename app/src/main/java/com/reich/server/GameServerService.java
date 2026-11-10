package com.reich.server;

import java.io.File;

import com.elvarg.Server;
import com.elvarg.game.GameConstants;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * The foreground service that hosts the game server.
 * <p>
 * Running the server inside a foreground service keeps the process alive (and
 * therefore the server online) even when the application is not in the
 * foreground. The service additionally holds a partial wake lock and a wifi
 * lock so that the CPU and the wifi radio keep running while the device is
 * idle.
 */
public class GameServerService extends Service {

    /**
     * The action that starts the server.
     */
    public static final String ACTION_START = "com.reich.server.action.START";
    /**
     * The action that stops the server.
     */
    public static final String ACTION_STOP = "com.reich.server.action.STOP";
    /**
     * The extra that holds the port the server has to bind to.
     */
    public static final String EXTRA_PORT = "com.reich.server.extra.PORT";

    /**
     * The id of the notification that shows the state of the server.
     */
    private static final int NOTIFICATION_ID = 317;
    /**
     * The id of the notification channel of the server.
     */
    private static final String CHANNEL_ID = "server_status";
    /**
     * The tag of the partial wake lock.
     */
    private static final String WAKE_LOCK_TAG = "Reich317:GameServer";
    /**
     * The tag of the wifi lock.
     */
    private static final String WIFI_LOCK_TAG = "Reich317:GameServerWifi";

    private static final String TAG = ServerLog.TAG;

    private ServerPreferences preferences;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private Thread serverThread;
    private volatile boolean running;
    private volatile boolean stopping;
    private int boundPort = ServerPreferences.DEFAULT_PORT;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = new ServerPreferences(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            // The foreground requirement has to be fulfilled even when the
            // service is only asked to stop.
            startForegroundNotification("Stopping the server...");
            stopServer();
            return START_NOT_STICKY;
        }

        if (intent == null) {
            // The service was restarted by the system, only keep it alive
            // when the server was running before the process was killed.
            if (!preferences.wasRunning()) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        int port = intent == null ? -1 : intent.getIntExtra(EXTRA_PORT, -1);
        if (port <= 0 || port > 65535) {
            port = preferences.getPort();
        }
        boundPort = port;

        // The notification has to be posted within a few seconds of the
        // service being started, otherwise the system kills the service.
        startForegroundNotification("Starting the server...");

        if (!running) {
            running = true;
            startServer(port);
        }

        // The service is restarted automatically when the system kills it.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        releaseLocks();
        try {
            Server.stop();
        } catch (Throwable t) {
            Log.e(TAG, "Unable to stop the server cleanly", t);
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // The server is supposed to keep running in the background, so the
        // service is intentionally not stopped here.
        super.onTaskRemoved(rootIntent);
    }

    /**
     * Starts the server on a background thread.
     *
     * @param port the port to bind to.
     */
    private void startServer(int port) {
        ServerRuntime.setPort(port);
        ServerRuntime.setState(ServerRuntime.State.STARTING, "Loading the server data...");

        serverThread = new Thread(() -> {
            try {
                File dataDirectory = AssetInstaller.install(this, (percent, message) ->
                        ServerLog.info(message + " (" + percent + "%)"));

                GameConstants.setDataDirectory(dataDirectory.getAbsolutePath());

                ServerLog.info("Starting the server on port " + port + "...");
                Server.start(port);

                if (stopping) {
                    // The server was asked to stop while it was still starting.
                    Server.stop();
                    return;
                }

                preferences.setRunning(true);
                ServerRuntime.setState(ServerRuntime.State.RUNNING,
                        "Online on port " + port + " (" + NetworkUtils.getConnectionType(this) + ")");
                ServerLog.info("The server is now online on port " + port + ".");
                updateNotification();
            } catch (Throwable t) {
                Log.e(TAG, "The server could not be started", t);
                ServerLog.error("The server could not be started: " + t, t);
                preferences.setRunning(false);
                running = false;
                ServerRuntime.setState(ServerRuntime.State.ERROR, String.valueOf(t.getMessage()));
                updateNotification();
                stopSelf();
            }
        }, "ReichServerThread");
        serverThread.setDaemon(false);
        serverThread.start();
    }

    /**
     * Stops the server and shuts this service down.
     */
    private void stopServer() {
        if (stopping) {
            return;
        }
        stopping = true;

        ServerRuntime.setState(ServerRuntime.State.STARTING, "Stopping the server...");
        updateNotification();

        Thread thread = new Thread(() -> {
            try {
                Server.stop();
            } catch (Throwable t) {
                Log.e(TAG, "Unable to stop the server cleanly", t);
            }
            preferences.setRunning(false);
            running = false;
            ServerRuntime.setState(ServerRuntime.State.STOPPED, "The server is not running.");
            releaseLocks();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }, "ReichServerStopThread");
        thread.start();
    }

    /**
     * Starts the server service.
     *
     * @param context the context to start the service with.
     * @param port    the port to bind to.
     */
    public static void start(Context context, int port) {
        Intent intent = new Intent(context, GameServerService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_PORT, port);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Stops the server service.
     *
     * @param context the context to stop the service with.
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, GameServerService.class);
        intent.setAction(ACTION_STOP);
        try {
            // Delivering the stop command to an already running service does
            // not start it, so it is allowed from the background as well.
            context.startService(intent);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to deliver the stop command, falling back", t);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
    }

    /**
     * Creates the notification channel of the server.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Game server",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows the state of the game server.");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    /**
     * Builds the notification that shows the state of the server.
     *
     * @param message the message shown below the title.
     * @return the notification.
     */
    private Notification buildNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Reich 317 Server")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_stat_server)
                .setContentIntent(contentIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        PendingIntent stopIntent = PendingIntent.getService(this, 1,
                new Intent(this, GameServerService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.addAction(0, "Stop", stopIntent);
        return builder.build();
    }

    /**
     * @return the intent that opens the main activity.
     */
    private PendingIntent contentIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Puts the service into the foreground.
     * <p>
     * From Android 14 (API 34) onwards the foreground service type has to be
     * passed explicitly, the type has to be a subset of the type that is
     * declared in the manifest.
     *
     * @param message the message shown in the notification.
     */
    private void startForegroundNotification(String message) {
        acquireLocks();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(message),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(message));
        }
    }

    /**
     * Refreshes the notification with the current state of the server.
     */
    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        String message = ServerRuntime.getDetail();
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(message));
        } catch (Throwable t) {
            Log.e(TAG, "Unable to update the notification", t);
        }
    }

    /**
     * Acquires the wake lock and the wifi lock, both of them are optional
     * and can be disabled by the user.
     */
    private void acquireLocks() {
        if (preferences.isWakeLockEnabled() && wakeLock == null) {
            try {
                PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                    wakeLock.setReferenceCounted(false);
                    wakeLock.acquire();
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unable to acquire the wake lock", t);
            }
        }

        if (preferences.isWifiLockEnabled() && wifiLock == null) {
            try {
                WifiManager wifiManager = (WifiManager) getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                            WIFI_LOCK_TAG);
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unable to acquire the wifi lock", t);
            }
        }
    }

    /**
     * Releases the wake lock and the wifi lock.
     */
    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable ignored) {
                // Nothing to do, the lock is gone.
            }
        }
        wakeLock = null;

        if (wifiLock != null && wifiLock.isHeld()) {
            try {
                wifiLock.release();
            } catch (Throwable ignored) {
                // Nothing to do, the lock is gone.
            }
        }
        wifiLock = null;
    }
}
