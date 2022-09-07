package org.microhttp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Stream;

public class RequestParserTest {

    static final byte[] GET_BYTES = """
            GET /file HTTP/1.1\r
            Host: 127.0.0.1:9000\r
            Accept: */*\r
            \r
            """.getBytes();

    static final Request GET_REQUEST = new Request(
            "GET",
            "/file",
            "HTTP/1.1",
            List.of(
                    new Header("Host", "127.0.0.1:9000"),
                    new Header("Accept", "*/*")),
            null);

    static final byte[] POST_BYTES = """
            POST /file HTTP/1.1\r
            Host: 127.0.0.1:9000\r
            Accept: */*\r
            Content-Length: 11\r
            Content-Type: text/plain\r
            \r
            hello world""".getBytes();

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

    static final byte[] CHUNKED_POST_BYTES = """
            POST /file HTTP/1.1\r
            Host: 127.0.0.1:9000\r
            Accept: */*\r
            Transfer-Encoding: chunked\r
            Content-Type: text/plain\r
            \r
            5\r
            hello\r
            1\r
             \r
            5\r
            world\r
            0\r
            \r
            """.getBytes();

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

    static final byte[] INVALID_CONTENT_LENGTH = """
            POST /file HTTP/1.1\r
            Content-Length: abc\r
            \r
            hello world""".getBytes();

    static final byte[] INVALID_HEADER = """
            GET /file HTTP/1.1\r
            abc123\r
            \r
            """.getBytes();

    static final byte[] INVALID_HEADER_NAME = """
            GET /file HTTP/1.1\r
            : Value\r
            \r
            """.getBytes();

    static final byte[] INVALID_HEADER_VALUE = """
            GET /file HTTP/1.1\r
            Name\r
            \r
            """.getBytes();

    static final byte[] INVALID_CHUNK_SIZE = """
            POST /file HTTP/1.1\r
            Host: 127.0.0.1:9000\r
            Accept: */*\r
            Transfer-Encoding: chunked\r
            Content-Type: text/plain\r
            \r
            $\r
            hello\r
            0\r
            \r
            """.getBytes();

    // https://snyk.io/blog/demystifying-http-request-smuggling/
    static final byte[] DOUBLE_CONTENT_LENGTH_SMUGGLE = """
            GET /file HTTP/1.1\r
            Content-Length: 0\r
            Content-Length: 43\r
            Host: snyk.io\r
            \r
            GET /reqsmuggle HTTP/1.1\r
            Host: snyk.io\r
            \r
            """.getBytes();

    static Stream<Arguments> requestArgsProvider() {
        return Stream.of(
                Arguments.arguments(GET_BYTES, GET_REQUEST),
                Arguments.arguments(POST_BYTES, POST_REQUEST),
                Arguments.arguments(CHUNKED_POST_BYTES, CHUNKED_POST_REQUEST));
    }

    static Stream<byte[]> invalidRequestArgsProvider() {
        return Stream.of(
                INVALID_CONTENT_LENGTH,
                INVALID_HEADER,
                INVALID_HEADER_NAME,
                INVALID_HEADER_VALUE,
                INVALID_CHUNK_SIZE,
                DOUBLE_CONTENT_LENGTH_SMUGGLE);
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
