package org.microhttp;

import static org.microhttp.CloseUtils.closeQuietly;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EventLoop is an HTTP server implementation. It provides connection management, network I/O,
 * request parsing, and request dispatching.
 */
public class EventLoop {

    private final Options options;
    private final Logger logger;

    private final Selector selector;
    private final AtomicBoolean stop;
    private final ServerSocketChannel serverSocketChannel;
    private final List<ConnectionEventLoop> connectionEventLoops;
    private final Thread thread;

    public EventLoop(Handler handler) throws IOException {
        this(Options.builder().build(), handler);
    }

    public EventLoop(Options options, Handler handler) throws IOException {
        this(options, NoopLogger.instance(), handler);
    }

    public EventLoop(Options options, Logger logger, Handler handler) throws IOException {
        this.options = options;
        this.logger = logger;

        selector = Selector.open();
        stop = new AtomicBoolean();

        AtomicLong connectionCounter = new AtomicLong();
        connectionEventLoops = new ArrayList<>();
        for (int i = 0; i < options.concurrency(); i++) {
            connectionEventLoops.add(new ConnectionEventLoop(options, logger, handler, connectionCounter, stop));
        }

        thread = new Thread(this::run, "event-loop");

        InetSocketAddress address = options.host() == null
                ? new InetSocketAddress(options.port()) // wildcard address
                : new InetSocketAddress(options.host(), options.port());

        serverSocketChannel = ServerSocketChannel.open();
        if (options.reuseAddr()) {
            serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, options.reuseAddr());
        }
        if (options.reusePort()) {
            serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEPORT, options.reusePort());
        }
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(address, options.acceptLength());
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public int getPort() throws IOException {
        return serverSocketChannel.getLocalAddress() instanceof InetSocketAddress a ? a.getPort() : -1;
    }

    public void start() {
        thread.start();
        connectionEventLoops.forEach(ConnectionEventLoop::start);
    }

    private void run() {
        try {
            doRun();
        } catch (IOException e) {
            if (logger.enabled()) {
                logger.log(e, new LogEntry("event", "event_loop_terminate"));
            }
            stop.set(true); // stop the world on critical error
        } finally {
            closeQuietly(selector);
            closeQuietly(serverSocketChannel);
        }
    }

    private void doRun() throws IOException {
        while (!stop.get()) {
            selector.select(options.resolution().toMillis());
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> it = selectedKeys.iterator();
            while (it.hasNext()) {
                SelectionKey selKey = it.next();
                if (selKey.isAcceptable()) {
                    ConnectionEventLoop connectionEventLoop = leastConnections();
                    connectionEventLoop.register(serverSocketChannel.accept());
                }
                it.remove();
            }
        }
    }

    private ConnectionEventLoop leastConnections() {
        return connectionEventLoops.stream()
                .min(Comparator.comparing(ConnectionEventLoop::numConnections))
                .get();
    }

    public void stop() {
        stop.set(true);
    }

    public void join() throws InterruptedException {
        thread.join();
        for (ConnectionEventLoop connectionEventLoop : connectionEventLoops) {
            connectionEventLoop.join();
        }
    }
}
