package org.microhttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * EventLoop is an HTTP server implementation. It provides connection management, network I/O,
 * request parsing, and request dispatching.
 * <p>
 * The diagram below outlines the various connection states.
 *
 * <pre>
 *                                                   Write Complete Non-Persistent
 *                                   Write     +--------------------------------------------+
 *                                   Complete  |                                            |
 *              Read                 Request   |                Write                       |
 *              Partial              Pipelined |                Partial                     |
 *              +-----+                +-----+ |                +-----+                     |
 *              |     |                |     | |                |     |    Write            |
 *              |     v                |     v |                |     v    Complete         v
 *            +-+--------+  Read-     ++-------+-+  Write-    +-+--------+ Non-       +----------+
 *    Accept  |          |  Complete  |          |  Partial   |          | Persist.   |          |
 * ---------->| READABLE +----------->| DISPATCH +----------->| WRITABLE +----------->|  CLOSED  |
 *            |          |            |          |            |          |            |          |
 *            +----+-----+            +----------+ Write      +-+---+----+            +----------+
 *                 |                        ^      Complete     |   |
 *                 |                        |      Request      |   |
 *                 |                        |      Pipelined    |   |
 *                 |                        +-------------------+   |
 *                 |                                                |
 *                 +------------------------------------------------+
 *                               Write Complete Persistent
 * </pre>
 */
public class EventLoop {

    private final Options options;
    private final Logger logger;
    private final Handler handler;

    private final Scheduler timeoutQueue;
    private final Queue<Runnable> taskQueue;
    private final ByteBuffer readBuffer;
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    private long connectionCounter;

    private Thread eventLoopThread;
    private volatile boolean stop;

    public EventLoop(Handler handler) throws IOException {
        this(new Options(), handler);
    }

    public EventLoop(Options options, Handler handler) throws IOException {
        this(options, new DebugLogger(), handler);
    }

    public EventLoop(Options options, Logger logger, Handler handler) throws IOException {
        this.options = options;
        this.logger = logger;
        this.handler = handler;

        timeoutQueue = new Scheduler();
        taskQueue = new ConcurrentLinkedQueue<>();
        readBuffer = ByteBuffer.allocateDirect(options.readBufferSize());
        selector = Selector.open();

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

    private class Connection {
        static final String HTTP_1_0 = "HTTP/1.0";
        static final String HTTP_1_1 = "HTTP/1.1";

        static final String HEADER_CONNECTION = "Connection";
        static final String HEADER_CONTENT_LENGTH = "Content-Length";

        static final String KEEP_ALIVE = "Keep-Alive";

        final SocketChannel socketChannel;
        final SelectionKey selectionKey;
        final ByteTokenizer byteTokenizer;
        final String id;
        RequestParser requestParser;
        ByteBuffer writeBuffer;
        Cancellable requestTimeoutTask;
        boolean httpOneDotZero;
        boolean keepAlive;

        private Connection(SocketChannel socketChannel, SelectionKey selectionKey) {
            this.socketChannel = socketChannel;
            this.selectionKey = selectionKey;
            byteTokenizer = new ByteTokenizer();
            id = Long.toString(connectionCounter++);
            requestParser = new RequestParser(byteTokenizer);
            requestTimeoutTask = timeoutQueue.schedule(this::onRequestTimeout, options.requestTimeout());
        }

        private void onRequestTimeout() {
            if (logger.enabled()) {
                logger.log(
                        new LogEntry("event", "request_timeout"),
                        new LogEntry("id", id));
            }
            failSafeClose();
        }

        private void onReadable() {
            try {
                doOnReadable();
            } catch (IOException | RuntimeException e) {
                if (logger.enabled()) {
                    logger.log(e,
                            new LogEntry("event", "read_error"),
                            new LogEntry("id", id));
                }
                failSafeClose();
            }
        }

        private void doOnReadable() throws IOException {
            readBuffer.clear();
            int numBytes = socketChannel.read(readBuffer);
            if (numBytes < 0) {
                if (logger.enabled()) {
                    logger.log(
                            new LogEntry("event", "read_close"),
                            new LogEntry("id", id));
                }
                failSafeClose();
                return;
            }
            readBuffer.flip();
            byteTokenizer.add(readBuffer);
            if (logger.enabled()) {
                logger.log(
                        new LogEntry("event", "read_bytes"),
                        new LogEntry("id", id),
                        new LogEntry("read_bytes", Integer.toString(numBytes)),
                        new LogEntry("request_bytes", Integer.toString(byteTokenizer.remaining())));
            }
            if (requestParser.parse()) {
                if (logger.enabled()) {
                    logger.log(
                            new LogEntry("event", "read_request"),
                            new LogEntry("id", id),
                            new LogEntry("request_bytes", Integer.toString(byteTokenizer.remaining())));
                }
                onParseRequest();
            } else {
                if (byteTokenizer.size() > options.maxRequestSize()) {
                    if (logger.enabled()) {
                        logger.log(
                                new LogEntry("event", "exceed_request_max_close"),
                                new LogEntry("id", id),
                                new LogEntry("request_size", Integer.toString(byteTokenizer.size())));
                    }
                    failSafeClose();
                }
            }
        }

        private void onParseRequest() {
            if (selectionKey.interestOps() != 0) {
                selectionKey.interestOps(0);
            }
            if (requestTimeoutTask != null) {
                requestTimeoutTask.cancel();
                requestTimeoutTask = null;
            }
            Request request = requestParser.request();
            httpOneDotZero = request.version().equalsIgnoreCase(HTTP_1_0);
            keepAlive = request.hasHeader(HEADER_CONNECTION, KEEP_ALIVE);
            byteTokenizer.compact();
            requestParser = new RequestParser(byteTokenizer);
            handler.handle(request, this::onResponse);
        }

        private void onResponse(Response response) {
            // enqueuing the callback invocation and waking the selector
            // ensures that the response callback works properly when
            // invoked inline from the event loop thread or a separate background thread
            taskQueue.add(() -> {
                try {
                    prepareToWriteResponse(response);
                } catch (IOException e) {
                    if (logger.enabled()) {
                        logger.log(e,
                                new LogEntry("event", "response_ready_error"),
                                new LogEntry("id", id));
                    }
                    failSafeClose();
                }
            });
            // selector wakeup is not necessary if callback was invoked within event loop thread
            // since scheduler tasks are processed at the end of every event loop iteration
            if (Thread.currentThread() != eventLoopThread) {
                selector.wakeup();
            }
        }

        private void prepareToWriteResponse(Response response) throws IOException {
            String version = httpOneDotZero ? HTTP_1_0 : HTTP_1_1;
            List<Header> headers = new ArrayList<>();
            if (httpOneDotZero && keepAlive) {
                headers.add(new Header(HEADER_CONNECTION, KEEP_ALIVE));
            }
            if (!response.hasHeader(HEADER_CONTENT_LENGTH)) {
                headers.add(new Header(HEADER_CONTENT_LENGTH, Integer.toString(response.body().length)));
            }
            writeBuffer = ByteBuffer.wrap(response.serialize(version, headers));
            if (logger.enabled()) {
                logger.log(
                        new LogEntry("event", "response_ready"),
                        new LogEntry("id", id),
                        new LogEntry("num_bytes", Integer.toString(writeBuffer.remaining())));
            }
            doOnWritable();
        }

        private void onWritable() {
            try {
                doOnWritable();
            } catch (IOException | RuntimeException e) {
                if (logger.enabled()) {
                    logger.log(e,
                            new LogEntry("event", "write_error"),
                            new LogEntry("id", id));
                }
                failSafeClose();
            }
        }

        private void doOnWritable() throws IOException {
            int numBytes = socketChannel.write(writeBuffer);
            if (!writeBuffer.hasRemaining()) { // response fully written
                writeBuffer = null; // done with current write buffer, remove reference
                if (logger.enabled()) {
                    logger.log(
                            new LogEntry("event", "write_response"),
                            new LogEntry("id", id),
                            new LogEntry("num_bytes", Integer.toString(numBytes)));
                }
                if (httpOneDotZero && !keepAlive) { // non-persistent connection, close now
                    if (logger.enabled()) {
                        logger.log(
                                new LogEntry("event", "close_after_response"),
                                new LogEntry("id", id));
                    }
                    failSafeClose();
                } else { // persistent connection
                    if (requestParser.parse()) { // subsequent request in buffer
                        if (logger.enabled()) {
                            logger.log(
                                    new LogEntry("event", "pipeline_request"),
                                    new LogEntry("id", id),
                                    new LogEntry("request_bytes", Integer.toString(byteTokenizer.remaining())));
                        }
                        onParseRequest();
                    } else { // switch back to read mode
                        requestTimeoutTask = timeoutQueue.schedule(this::onRequestTimeout, options.requestTimeout());
                        selectionKey.interestOps(SelectionKey.OP_READ);
                    }
                }
            } else { // response not fully written, switch to or remain in write mode
                if ((selectionKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                    selectionKey.interestOps(SelectionKey.OP_WRITE);
                }
                if (logger.enabled()) {
                    logger.log(
                            new LogEntry("event", "write"),
                            new LogEntry("id", id),
                            new LogEntry("num_bytes", Integer.toString(numBytes)));
                }
            }
        }

        private void failSafeClose() {
            try {
                if (requestTimeoutTask != null) {
                    requestTimeoutTask.cancel();
                }
                selectionKey.cancel();
                socketChannel.close();
            } catch (IOException e) {
                // suppress error
            }
        }
    }

    public void start() {
        try {
            doStart();
        } catch (IOException e) {
            if (logger.enabled()) {
                logger.log(e, new LogEntry("event", "terminate"));
            }
        }
    }

    public void doStart() throws IOException {
        eventLoopThread = Thread.currentThread();
        while (!stop) {
            selector.select(options.resolution().toMillis());
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> it = selectedKeys.iterator();
            while (it.hasNext()) {
                SelectionKey selKey = it.next();
                if (selKey.isAcceptable()) {
                    onAcceptable();
                } else if (selKey.isReadable()) {
                    ((Connection) selKey.attachment()).onReadable();
                } else if (selKey.isWritable()) {
                    ((Connection) selKey.attachment()).onWritable();
                }
                it.remove();
            }
            timeoutQueue.expired().forEach(Runnable::run);
            Runnable task;
            while ((task = taskQueue.poll()) != null) {
                task.run();
            }
        }
    }

    public void stop() {
        stop = true;
    }

    private void onAcceptable() throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
        Connection connection = new Connection(socketChannel, selectionKey);
        selectionKey.attach(connection);
        if (logger.enabled()) {
            logger.log(
                    new LogEntry("event", "accept"),
                    new LogEntry("remote_address", socketChannel.getRemoteAddress().toString()),
                    new LogEntry("id", connection.id));
        }
    }
}
