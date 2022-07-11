package org.microhttp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EventLoopTest {

    static TestLogger logger;
    static ExecutorService executor;
    static int port;
    static EventLoop eventLoop;

    static final String HTTP10_REQUEST = """
            GET /file HTTP/1.0\r
            \r
            """;

    static final String HTTP10_KEEP_ALIVE_REQUEST = """
            GET /file HTTP/1.0\r
            Connection: Keep-Alive\r
            \r
            """;

    static final String HTTP10_KEEP_ALIVE_RESPONSE = """
            HTTP/1.0 200 OK\r
            Connection: Keep-Alive\r
            Content-Length: 12\r
            Content-Type: text/plain\r
            \r
            hello world
            """;

    static final String HTTP10_RESPONSE = """
            HTTP/1.0 200 OK\r
            Content-Length: %d\r
            Content-Type: text/plain\r
            \r
            %s""";

    static final String HTTP11_REQUEST = """
            GET /file HTTP/1.1\r
            \r
            """;

    static final String HTTP11_RESPONSE = """
            HTTP/1.1 200 OK\r
            Content-Length: %d\r
            Content-Type: text/plain\r
            \r
            %s""";

    Socket socket;
    InputStream inputStream;
    OutputStream outputStream;

    static volatile String responseBody;

    @BeforeAll
    static void beforeAll() throws IOException {
        logger = new TestLogger();
        executor = Executors.newFixedThreadPool(1);
        Options options = new Options()
                .withPort(0)
                .withRequestTimeout(Duration.ofMillis(2_500))
                .withReadBufferSize(1_024)
                .withMaxRequestSize(2_048);
        eventLoop = new EventLoop(
                options,
                logger,
                (req, callback) -> executor.execute(() -> callback.accept(new Response(
                        200,
                        "OK",
                        List.of(new Header("Content-Type", "text/plain")),
                        responseBody.getBytes()))));
        eventLoop.start();
        port = eventLoop.getPort();
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        eventLoop.stop();
        eventLoop.join();
        executor.shutdown();
    }

    @BeforeEach
    void beforeEach() throws IOException {
        responseBody = "hello world\n";
        logger.clear();
        socket = new Socket("localhost", port);
        socket.setSoTimeout(5_000);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    @AfterEach
    void afterEach() throws IOException {
        socket.close();
    }

    @Test
    void http10RequestNonPersistent() throws IOException {
        outputStream.write(HTTP10_REQUEST.getBytes());
        byte[] received = inputStream.readAllBytes();
        Assertions.assertArrayEquals(HTTP10_RESPONSE.formatted(responseBody.length(), responseBody).getBytes(), received);
        Assertions.assertTrue(logger.hasEventLog("close_after_response"));
    }

    @ParameterizedTest
    @ValueSource(ints = {100_000, 1_000_000, 10_000_000})
    void largeResponsePayloads(int size) throws IOException {
        responseBody = IntStream.range(0, size).mapToObj(n -> "x").collect(Collectors.joining());
        outputStream.write(HTTP10_REQUEST.getBytes());
        byte[] received = inputStream.readAllBytes();
        Assertions.assertArrayEquals(HTTP10_RESPONSE.formatted(responseBody.length(), responseBody).getBytes(), received);
        Assertions.assertTrue(logger.hasEventLog("close_after_response"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void http10RequestPersistent(int numRequests) throws IOException {
        for (int i = 0; i < numRequests; i++) {
            outputStream.write(HTTP10_KEEP_ALIVE_REQUEST.getBytes());
            byte[] received = inputStream.readNBytes(HTTP10_KEEP_ALIVE_RESPONSE.length());
            Assertions.assertArrayEquals(HTTP10_KEEP_ALIVE_RESPONSE.getBytes(), received);
        }
    }

    @Test
    void http11Request() throws IOException {
        outputStream.write(HTTP11_REQUEST.getBytes());
        socket.shutdownOutput();
        byte[] received = inputStream.readAllBytes();
        Assertions.assertArrayEquals(HTTP11_RESPONSE.formatted(responseBody.length(), responseBody).getBytes(), received);
        Assertions.assertTrue(logger.hasEventLog("read_close"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void http11RequestsPipelined(int numRequests) throws IOException {
        String request = IntStream.range(0, numRequests).mapToObj(n -> HTTP11_REQUEST).collect(Collectors.joining());
        outputStream.write(request.getBytes());
        socket.shutdownOutput();
        byte[] received = inputStream.readAllBytes();
        String expected = IntStream.range(0, numRequests)
                .mapToObj(n -> HTTP11_RESPONSE.formatted(responseBody.length(), responseBody))
                .collect(Collectors.joining());
        Assertions.assertArrayEquals(expected.getBytes(), received);
        Assertions.assertEquals(numRequests - 1, logger.countEventLogs("pipeline_request"));
    }

    @Test
    void timeoutOnServer() throws IOException {
        int data = inputStream.read();
        Assertions.assertEquals(-1, data);
        Assertions.assertTrue(logger.hasEventLog("request_timeout"));
    }

    @Test
    void requestTooLarge() throws IOException {
        int length = 3_072;
        char[] arr = new char[length];
        Arrays.fill(arr, 'x');
        String request = """
                POST /file HTTP/1.0\r
                Content-Length: %d\r
                \r
                %s
                """.formatted(length, new String(arr));
        outputStream.write(request.getBytes());
        try {
            // read processed prior to receipt of RST packet
            int data = inputStream.read();
            Assertions.assertEquals(-1, data);
        } catch (SocketException e) {
            // read processed after receipt of RST packet
            Assertions.assertTrue(e.getMessage().contains("reset"));
        }
        Assertions.assertTrue(logger.hasEventLog("exceed_request_max_close"));
    }

    static class TestLogger implements Logger {
        final List<List<LogEntry>> events = new ArrayList<>();

        synchronized boolean hasEventLog(String value) {
            return hasLog(ents -> ents.stream().anyMatch(e -> e.key().equals("event") && e.value().equals(value)));
        }

        synchronized long countEventLogs(String value) {
            return countLogs(ents -> ents.stream().anyMatch(e -> e.key().equals("event") && e.value().equals(value)));
        }

        synchronized boolean hasLog(Predicate<List<LogEntry>> predicate) {
            return events.stream().anyMatch(predicate);
        }

        synchronized long countLogs(Predicate<List<LogEntry>> predicate) {
            return events.stream().filter(predicate).count();
        }

        synchronized void clear() {
            events.clear();
        }

        @Override
        public synchronized boolean enabled() {
            return true;
        }

        @Override
        public synchronized void log(LogEntry... entries) {
            events.add(List.of(entries));
        }

        @Override
        public synchronized void log(Exception e, LogEntry... entries) {
            log(entries);
        }
    }

}
