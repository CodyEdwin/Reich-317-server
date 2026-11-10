package com.elvarg;

import com.elvarg.game.GameBuilder;
import com.elvarg.game.GameConstants;
import com.elvarg.net.NetworkBuilder;
import com.elvarg.net.NetworkConstants;
import com.elvarg.util.flood.Flooder;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The starting point of the application. Initializes the bootstrap.
 * <p>
 * The server can either be started through the {@link #main(String[])} entry
 * point (desktop JVM) or through {@link #start(int)} (used by the Android
 * application which runs the server inside a foreground service).
 *
 * @author Professor Oak
 * @author Lare96
 */
public class Server {

    /**
     * The flooder used to stress-test the server.
     */
    private static final Flooder flooder = new Flooder();
    /**
     * Is the server running in production mode?
     */
    public static boolean PRODUCTION = false;
    /**
     * The logger that will print important information.
     */
    private static Logger logger = Logger.getLogger(Server.class.getSimpleName());
    /**
     * The flag that determines if the server is currently being updated or not.
     */
    private static boolean updating = false;
    /**
     * The game builder that holds the game engine, only set while the server
     * is running.
     */
    private static GameBuilder gameBuilder;
    /**
     * The network builder that holds the bound channel, only set while the
     * server is running.
     */
    private static NetworkBuilder networkBuilder;
    /**
     * The port the server is currently bound to.
     */
    private static int port = NetworkConstants.GAME_PORT;

    /**
     * The main method that will put the server online.
     */
    public static void main(String[] args) {
        try {

            if (args.length == 1) {
                PRODUCTION = Integer.parseInt(args[0]) == 1;
            }

            // Allows the data directory to be overridden, this is used by the
            // desktop launcher and by the tests.
            String dataDirectory = System.getProperty("reich.data.dir");
            if (dataDirectory != null && !dataDirectory.trim().isEmpty()) {
                GameConstants.setDataDirectory(dataDirectory);
            }

            // Allows the port to be overridden, this is used by the desktop
            // launcher and by the tests.
            int gamePort = NetworkConstants.GAME_PORT;
            String portProperty = System.getProperty("reich.port");
            if (portProperty != null && !portProperty.trim().isEmpty()) {
                gamePort = Integer.parseInt(portProperty.trim());
            }

            logger.info("Initializing " + GameConstants.NAME + " in " + (PRODUCTION ? "production" : "non-production") + " mode..");
            start(gamePort);
            logger.info(GameConstants.NAME + " is now online!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "An error occurred while binding the Bootstrap!", e);

            // No point in continuing server startup when the
            // bootstrap either failed to bind or was bound
            // incorrectly.
            System.exit(1);
        }
    }

    /**
     * Starts the server on the given port. This method blocks until the
     * server has been fully initialized, or until an error occurs.
     *
     * @param port the port to bind the game socket to.
     * @throws Exception if the server could not be started.
     */
    public static synchronized void start(int port) throws Exception {
        if (isRunning()) {
            throw new IllegalStateException("The server is already running!");
        }

        Server.port = port;
        logger.info("Initializing " + GameConstants.NAME + " in " + (PRODUCTION ? "production" : "non-production") + " mode..");

        gameBuilder = new GameBuilder();
        gameBuilder.initialize();

        networkBuilder = new NetworkBuilder();
        networkBuilder.initialize(port);
    }

    /**
     * Stops a running server. This shuts down the network layer, the game
     * engine and all of the background services, and saves the state of every
     * logged in player.
     */
    public static synchronized void stop() {
        if (!isRunning()) {
            return;
        }

        logger.info("Shutting " + GameConstants.NAME + " down..");

        try {
            com.elvarg.game.World.savePlayers();
            com.elvarg.game.content.clan.ClanChatManager.save();
        } catch (Throwable e) {
            logger.log(Level.WARNING, "An error occurred while saving the server state.", e);
        }

        if (networkBuilder != null) {
            networkBuilder.shutdown();
            networkBuilder = null;
        }

        if (gameBuilder != null) {
            gameBuilder.shutdown();
            gameBuilder = null;
        }

        com.elvarg.game.GameLogic.shutdown();

        logger.info(GameConstants.NAME + " has been shut down.");
    }

    /**
     * Determines whether the server is currently running.
     *
     * @return {@code true} if the server is online, {@code false} otherwise.
     */
    public static boolean isRunning() {
        return networkBuilder != null;
    }

    /**
     * The port the server is bound to.
     *
     * @return the port the server is bound to.
     */
    public static int getPort() {
        return port;
    }

    public static Logger getLogger() {
        return logger;
    }

    public static boolean isUpdating() {
        return updating;
    }

    public static void setUpdating(boolean isUpdating) {
        Server.updating = isUpdating;
    }

    public static Flooder getFlooder() {
        return flooder;
    }
}
