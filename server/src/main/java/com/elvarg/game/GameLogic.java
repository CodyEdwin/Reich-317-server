package com.elvarg.game;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

/**
 * A class used for handling operations that can be done
 * asynchronously.
 *
 * @author Professor Oak
 * @author Lare96
 */
public class GameLogic {

    /**
     * The logic service, used for carrying out
     * asynchronous tasks such as file-writing.
     */
    private static volatile ScheduledExecutorService logicService;

    /**
     * Submits a task to the logic service.
     *
     * @param t
     */
    public static void submit(Runnable t) {
        try {
            service().execute(t);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the logic service, creating it if it does not exist (or if it
     * has been shut down). This allows the server to be restarted within the
     * same process.
     *
     * @return the logic service.
     */
    private static synchronized ScheduledExecutorService service() {
        if (logicService == null || logicService.isShutdown()) {
            logicService = createLogicService();
        }
        return logicService;
    }

    /**
     * Creates the logic service.
     *
     * @return
     */
    public static ScheduledExecutorService createLogicService() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.setThreadFactory(new ThreadFactoryBuilder().setNameFormat("LogicServiceThread").build());
        executor.setKeepAliveTime(45, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        return Executors.unconfigurableScheduledExecutorService(executor);
    }

    /**
     * Shuts the logic service down. A new one is created automatically the
     * next time a task is submitted.
     */
    public static synchronized void shutdown() {
        if (logicService != null) {
            logicService.shutdownNow();
            logicService = null;
        }
    }
}
