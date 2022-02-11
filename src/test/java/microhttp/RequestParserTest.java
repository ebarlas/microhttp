package microhttp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Stream;

public class RequestParserTest {

    static final byte[] GET_BYTES = ("GET /file HTTP/1.1\r\n" +
            "Host: 127.0.0.1:9000\r\n" +
            "Accept: */*\r\n" +
            "\r\n").getBytes();

    static final Request GET_REQUEST = new Request(
            "GET",
            "/file",
            "HTTP/1.1",
            List.of(
                    new Header("Host", "127.0.0.1:9000"),
                    new Header("Accept", "*/*")),
            null);

    static final byte[] POST_BYTES = ("POST /file HTTP/1.1\r\n" +
            "Host: 127.0.0.1:9000\r\n" +
            "Accept: */*\r\n" +
            "Content-Length: 11\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "hello world").getBytes();

    static final Request POST_REQUEST = new Request(
            "POST",
            "/file",
            "HTTP/1.1",
            List.of(
                    new Header("Host", "127.0.0.1:9000"),
                    new Header("Accept", "*/*"),
                    new Header("Content-Length", "11"),
                    new Header("Content-Type", "text/plain")),
            "hello world".getBytes());

    static final byte[] CHUNKED_POST_BYTES = ("POST /file HTTP/1.1\r\n" +
            "Host: 127.0.0.1:9000\r\n" +
            "Accept: */*\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "5\r\n" +
            "hello\r\n" +
            "1\r\n" +
            " \r\n" +
            "5\r\n" +
            "world\r\n" +
            "0\r\n" +
            "\r\n").getBytes();

    static final Request CHUNKED_POST_REQUEST = new Request(
            "POST",
            "/file",
            "HTTP/1.1",
            List.of(
                    new Header("Host", "127.0.0.1:9000"),
                    new Header("Accept", "*/*"),
                    new Header("Transfer-Encoding", "chunked"),
                    new Header("Content-Type", "text/plain")),
            "hello world".getBytes());

    static final byte[] INVALID_CONTENT_LENGTH = ("POST /file HTTP/1.1\r\n" +
            "Content-Length: abc\r\n" +
            "\r\n" +
            "hello world").getBytes();

    static final byte[] INVALID_REQUEST_LINE = ("GET /file\r\n" +
            "\r\n").getBytes();

    static final byte[] INVALID_HEADER = ("GET /file HTTP/1.1\r\n" +
            "abc123\r\n" +
            "\r\n").getBytes();

    static final byte[] INVALID_CHUNK_SIZE = ("POST /file HTTP/1.1\r\n" +
            "Host: 127.0.0.1:9000\r\n" +
            "Accept: */*\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "Content-Type: text/plain\r\n" +
            "\r\n" +
            "$\r\n" +
            "hello\r\n" +
            "0\r\n" +
            "\r\n").getBytes();

    static Stream<Arguments> requestArgsProvider() {
        return Stream.of(
                Arguments.arguments(GET_BYTES, GET_REQUEST),
                Arguments.arguments(POST_BYTES, POST_REQUEST),
                Arguments.arguments(CHUNKED_POST_BYTES, CHUNKED_POST_REQUEST));
    }

    static Stream<Arguments> invalidRequestArgsProvider() {
        return Stream.of(
                Arguments.arguments(INVALID_REQUEST_LINE),
                Arguments.arguments(INVALID_CONTENT_LENGTH),
                Arguments.arguments(INVALID_HEADER),
                Arguments.arguments(INVALID_CHUNK_SIZE));
    }

    @ParameterizedTest
    @MethodSource("requestArgsProvider")
    void completeRequests(byte[] requestToParse, Request expectedResult) {
        ByteTokenizer tokenizer = new ByteTokenizer();
        tokenizer.add(ByteBuffer.wrap(requestToParse));
        RequestParser parser = new RequestParser(tokenizer);
        Assertions.assertTrue(parser.parse());
        assertEquals(expectedResult, parser.request());
    }

    @ParameterizedTest
    @MethodSource("requestArgsProvider")
    void partialRequests(byte[] requestToParse, Request expectedResult) {
        ByteTokenizer tokenizer = new ByteTokenizer();
        RequestParser parser = new RequestParser(tokenizer);
        for (int i = 0; i < requestToParse.length - 1; i++) {
            tokenizer.add(ByteBuffer.wrap(new byte[]{requestToParse[i]}));
            Assertions.assertFalse(parser.parse());
        }
        tokenizer.add(ByteBuffer.wrap(new byte[]{requestToParse[requestToParse.length - 1]}));
        Assertions.assertTrue(parser.parse());
        assertEquals(expectedResult, parser.request());
    }

    @ParameterizedTest
    @MethodSource("invalidRequestArgsProvider")
    void invalidRequests(byte[] requestToParse) {
        ByteTokenizer tokenizer = new ByteTokenizer();
        RequestParser parser = new RequestParser(tokenizer);
        tokenizer.add(ByteBuffer.wrap(requestToParse));
        Assertions.assertThrowsExactly(IllegalStateException.class, parser::parse);
    }

    static void assertEquals(Request expected, Request observed) {
        Assertions.assertEquals(expected.method(), observed.method());
        Assertions.assertEquals(expected.uri(), observed.uri());
        Assertions.assertEquals(expected.version(), observed.version());
        Assertions.assertEquals(expected.headers(), observed.headers());
        Assertions.assertArrayEquals(expected.body(), observed.body());
    }
}
