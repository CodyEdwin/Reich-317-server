package com.elvarg.game;

import com.elvarg.game.content.clan.ClanChatManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The engine which processes the game.
 *
 * @author Lare96
 */
public final class GameEngine implements Runnable {

    /**
     * The {@link ScheduledExecutorService} which will be used for
     * this engine.
     */
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("GameThread").build());

    /**
     * Initializes this {@link GameEngine}.
     */
    public void init() {
        executorService.scheduleAtFixedRate(this, 0, GameConstants.GAME_ENGINE_PROCESSING_CYCLE_RATE,
                TimeUnit.MILLISECONDS);
    }

    /**
     * The amount of cycles that have been processed since the last automatic
     * save.
     */
    private int cycles;

    /**
     * Stops the world processing cycle.
     */
    public void shutdown() {
        executorService.shutdownNow();
    }

    @Override
    public void run() {
        try {
            World.process();

            // Save the characters every few minutes, this makes sure that
            // nothing is lost when the process of the application is killed
            // without the server being shut down properly.
            if (++cycles >= GameConstants.AUTO_SAVE_CYCLE_RATE) {
                cycles = 0;
                World.savePlayers();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            World.savePlayers();
            ClanChatManager.save();
        }
    }
}
