package com.reich.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Starts the server automatically after the device has been booted (or after
 * the application has been updated), when the user enabled that option.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = ServerLog.TAG;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        ServerPreferences preferences = new ServerPreferences(context);
        if (!preferences.isStartOnBootEnabled()) {
            return;
        }

        try {
            Log.i(TAG, "Starting the server after " + action);
            GameServerService.start(context, preferences.getPort());
        } catch (Throwable t) {
            Log.e(TAG, "Unable to start the server after boot", t);
        }
    }
}
