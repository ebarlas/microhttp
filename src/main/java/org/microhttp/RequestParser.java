package org.microhttp;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RequestParser {

    private static final Pattern REQUEST_LINE = Pattern.compile("([^ ]+) ([^ ]+) (.+)");
    private static final Pattern HEADER_LINE = Pattern.compile("([^:]+):[ ]*(.+)");

    private static final byte[] CRLF = "\r\n".getBytes();

    private static final String HEADER_CONTENT_LENGTH = "Content-Length";
    private static final String HEADER_TRANSFER_ENCODING = "Transfer-Encoding";
    private static final String CHUNKED = "chunked";

    private static final int RADIX_HEX = 16;

    enum State {
        REQUEST_LINE(p -> p.tokenizer.next(CRLF), (rp, token) -> rp.parseRequestLine(token)),
        HEADER(p -> p.tokenizer.next(CRLF), (rp, token) -> rp.parseHeader(token)),
        BODY(p -> p.tokenizer.next(p.contentLength), (rp, token) -> rp.parseBody(token)),
        CHUNK_SIZE(p -> p.tokenizer.next(CRLF), (rp, token) -> rp.parseChunkSize(token)),
        CHUNK_DATA(p -> p.tokenizer.next(p.chunkSize), (rp, token) -> rp.parseChunkData(token)),
        CHUNK_DATA_END(p -> p.tokenizer.next(CRLF), (rp, token) -> rp.parseChunkDateEnd()),
        CHUNK_TRAILER(p -> p.tokenizer.next(CRLF), (rp, token) -> rp.parseChunkTrailer()),
        DONE(null, null);

        final Function<RequestParser, byte[]> tokenSupplier;
        final BiConsumer<RequestParser, byte[]> tokenConsumer;

        State(Function<RequestParser, byte[]> tokenSupplier, BiConsumer<RequestParser, byte[]> tokenConsumer) {
            this.tokenSupplier = tokenSupplier;
            this.tokenConsumer = tokenConsumer;
        }
    }

    private final ByteTokenizer tokenizer;

    private State state = State.REQUEST_LINE;
    private int contentLength;
    private int chunkSize;
    private List<byte[]> chunks = new ArrayList<>();

    private String method;
    private String uri;
    private String version;
    private List<Header> headers = new ArrayList<>();
    private byte[] body;

    RequestParser(ByteTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    boolean parse() {
        while (state != State.DONE) {
            byte[] token = state.tokenSupplier.apply(this);
            if (token == null) {
                return false;
            }
            state.tokenConsumer.accept(this, token);
        }
        return true;
    }

    Request request() {
        return new Request(method, uri, version, headers, body);
    }

    private void parseRequestLine(byte[] token) {
        String line = new String(token);
        Matcher matcher = REQUEST_LINE.matcher(line);
        if (!matcher.matches()) {
            throw new IllegalStateException("malformed request line");
        }
        method = matcher.group(1);
        uri = matcher.group(2);
        version = matcher.group(3);
        state = State.HEADER;
    }

    private void parseHeader(byte[] token) {
        if (token.length == 0) { // CR-LF on own line, end of headers
            if (hasMultipleTransferLengths()) {
                throw new IllegalStateException("multiple message lengths");
            }
            OptionalInt contentLength = findContentLength();
            if (!contentLength.isPresent()) {
                if (hasChunkedEncodingHeader()) {
                    state = State.CHUNK_SIZE;
                } else {
                    state = State.DONE;
                }
            } else {
                this.contentLength = contentLength.getAsInt();
                state = State.BODY;
            }
        } else {
            String line = new String(token);
            Matcher matcher = HEADER_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalStateException("malformed header line");
            }
            headers.add(new Header(matcher.group(1), matcher.group(2)));
        }
    }

    private void parseChunkSize(byte[] token) {
        try {
            chunkSize = Integer.parseInt(new String(token), RADIX_HEX);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("invalid chunk size");
        }
        state = chunkSize == 0
                ? State.CHUNK_TRAILER
                : State.CHUNK_DATA;
    }

    private void parseChunkData(byte[] token) {
        chunks.add(token);
        state = State.CHUNK_DATA_END;
    }

    private void parseChunkDateEnd() {
        state = State.CHUNK_SIZE;
    }

    private void parseChunkTrailer() {
        mergeChunks();
        state = State.DONE;
    }

    private void parseBody(byte[] token) {
        body = token;
        state = State.DONE;
    }

    private void mergeChunks() {
        int size = chunks.stream().mapToInt(a -> a.length).sum();
        body = new byte[size];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, body, offset, chunk.length);
            offset += chunk.length;
        }
    }

    private boolean hasMultipleTransferLengths() {
        return headers.stream()
                .filter(h -> h.name().equalsIgnoreCase(HEADER_CONTENT_LENGTH) || h.name().equalsIgnoreCase(HEADER_TRANSFER_ENCODING))
                .count() > 1;
    }

    private OptionalInt findContentLength() {
        try {
            return headers.stream()
                    .filter(h -> h.name().equalsIgnoreCase(HEADER_CONTENT_LENGTH))
                    .map(Header::value)
                    .mapToInt(Integer::parseInt)
                    .findFirst();
        } catch (NumberFormatException e) {
            throw new IllegalStateException("invalid content-length header value");
        }
    }

    private boolean hasChunkedEncodingHeader() {
        return headers.stream()
                .filter(h -> h.name().equalsIgnoreCase(HEADER_TRANSFER_ENCODING))
                .map(Header::value)
                .anyMatch(v -> v.equalsIgnoreCase(CHUNKED));
    }

}
