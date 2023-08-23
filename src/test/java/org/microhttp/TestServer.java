package org.microhttp;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TestServer {

    final String response;
    final TestLogger logger;
    final ExecutorService executor;
    final int port;
    final EventLoop eventLoop;

    TestServer() throws IOException {
        this(false, 1_024, "hello world\n");
    }

    TestServer(boolean handleInline, int readBufferSize, String response) throws IOException {
        this.response = response;
        logger = new TestLogger();
        executor = Executors.newFixedThreadPool(1);
        Consumer<Consumer<Response>> c = callback -> callback.accept(new Response(
                200,
                "OK",
                List.of(new Header("Content-Type", "text/plain")),
                response.getBytes()));
        Handler h = handleInline
                ? (req, callback) -> c.accept(callback)
                : (req, callback) -> executor.execute(() -> c.accept(callback));
        Options options = Options.builder()
                .withPort(0)
                .withRequestTimeout(Duration.ofMillis(2_500))
                .withReadBufferSize(readBufferSize)
                .withMaxRequestSize(2_048)
                .build();
        eventLoop = new EventLoop(options, logger, h);
        eventLoop.start();
        port = eventLoop.getPort();
    }

    String response() {
        return response;
    }

    int port() {
        return port;
    }

    TestLogger logger() {
        return logger;
    }

    void stop() throws InterruptedException {
        eventLoop.stop();
        eventLoop.join();
        executor.shutdown();
    }

}
