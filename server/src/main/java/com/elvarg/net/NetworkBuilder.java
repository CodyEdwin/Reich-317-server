package com.elvarg.net;

import com.elvarg.net.channel.ChannelPipelineHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * The network builder for the Runescape #317 protocol. This class is used to
 * start and configure the {@link ServerBootstrap} that will control and manage
 * the entire network.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class NetworkBuilder {

    /**
     * The bootstrap that will oversee the management of the entire network.
     */
    private final ServerBootstrap bootstrap = new ServerBootstrap();

    /**
     * The event loop group that will be attached to the bootstrap.
     */
    private final EventLoopGroup loopGroup = new NioEventLoopGroup();

    /**
     * The {@link ChannelInitializer} that will determine how channels will be
     * initialized when registered to the event loop group.
     */
    private final ChannelInitializer<SocketChannel> channelInitializer = new ChannelPipelineHandler();

    /**
     * The channel the server is bound to, only set once the server has been
     * successfully initialized.
     */
    private Channel serverChannel;

    /**
     * Initializes this network handler effectively preparing the server to
     * listen for connections and handle network events.
     *
     * @param port the port that this network will be bound to.
     * @throws Exception if any issues occur while starting the network.
     */
    public void initialize(int port) throws IOException {
        ResourceLeakDetector.setLevel(Level.DISABLED);
        bootstrap.group(loopGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.childHandler(channelInitializer);
        serverChannel = bootstrap.bind(port).syncUninterruptibly().channel();
    }

    /**
     * Shuts this network handler down, closing the bound channel and
     * terminating the event loop group.
     */
    public void shutdown() {
        if (serverChannel != null) {
            try {
                serverChannel.close().syncUninterruptibly();
            } catch (Throwable ignored) {
                // The channel may already be closed.
            }
            serverChannel = null;
        }
        loopGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
    }
}
