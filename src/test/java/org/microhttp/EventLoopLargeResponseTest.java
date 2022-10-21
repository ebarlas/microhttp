package org.microhttp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class EventLoopLargeResponseTest {

    static final String REQUEST = """
            GET /file HTTP/1.0\r
            \r
            """;

    static final String RESPONSE = """
            HTTP/1.0 200 OK\r
            Content-Length: %d\r
            Content-Type: text/plain\r
            \r
            %s""";

    TestServer server;
    TestLogger logger;

    Socket socket;
    InputStream inputStream;
    OutputStream outputStream;

    void start(boolean handleInline, int readBufferSize, String response) throws IOException {
        this.server = new TestServer(handleInline, readBufferSize, response);
        logger = server.logger();
        logger.clear();
        socket = new Socket("localhost", server.port());
        socket.setSoTimeout(5_000);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    @AfterEach
    void afterEach() throws IOException, InterruptedException {
        socket.close();
        server.stop();
    }

    @ParameterizedTest
    @MethodSource("argsProvider")
    void largeResponsePayloads(boolean handleInline, int readBufferSize, int size) throws IOException {
        start(handleInline, readBufferSize, IntStream.range(0, size).mapToObj(n -> "x").collect(Collectors.joining()));
        outputStream.write(REQUEST.getBytes());
        byte[] received = inputStream.readAllBytes();
        Assertions.assertArrayEquals(RESPONSE.formatted(server.response().length(), server.response()).getBytes(), received);
        Assertions.assertTrue(logger.hasEventLog("close_after_response"));
    }

    static Stream<Arguments> argsProvider() {
        boolean[] handleInline = {true, false};
        int[] readBufferSize = {1_024, 1_024 * 1_024};
        int[] responseSize = {10_000, 100_000, 1_000_000};
        List<Arguments> args = new ArrayList<>();
        for (var hi : handleInline) {
            for (var rbs : readBufferSize) {
                for (var rs : responseSize) {
                    args.add(Arguments.of(hi, rbs, rs));
                }
            }
        }
        return args.stream();
    }

}
