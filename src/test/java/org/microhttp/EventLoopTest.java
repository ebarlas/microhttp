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
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EventLoopTest {

    static TestServer server;
    static TestLogger logger;
    static String responseBody;

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

    @BeforeAll
    static void beforeAll() throws IOException {
        server = new TestServer();
        logger = server.logger();
        responseBody = server.response();
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        server.stop();
    }

    @BeforeEach
    void beforeEach() throws IOException {
        logger.clear();
        socket = new Socket("localhost", server.port());
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
}
