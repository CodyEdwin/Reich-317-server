package com.reich.server;

import android.app.Application;

/**
 * The application of the server app. It installs the log bridge as early as
 * possible so that every message of the server ends up in the in-app console.
 */
public class ServerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Netty has to be configured before any of its classes are loaded.
        // Android does not expose sun.misc.Unsafe the same way a desktop JVM
        // does and direct buffers are not always reclaimable, so the safer
        // (heap based) code paths are used instead.
        System.setProperty("io.netty.noUnsafe", "true");
        System.setProperty("io.netty.noPreferDirect", "true");
        System.setProperty("io.netty.maxDirectMemory", "0");
        System.setProperty("io.netty.allocator.type", "unpooled");

        ServerLog.install();
    }
}
