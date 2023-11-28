package org.microhttp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

class RequestParser {

    private static final byte[] CRLF = "\r\n".getBytes();
    private static final byte[] SPACE = " ".getBytes();

    private static final String HEADER_CONTENT_LENGTH = "Content-Length";
    private static final String HEADER_TRANSFER_ENCODING = "Transfer-Encoding";
    private static final String CHUNKED = "chunked";

    private static final int RADIX_HEX = 16;

    enum State {
        METHOD(p -> p.tokenizer.next(SPACE), RequestParser::parseMethod),
        URI(p -> p.tokenizer.next(SPACE), RequestParser::parseUri),
        VERSION(p -> p.tokenizer.next(CRLF), RequestParser::parseVersion),
        HEADER(p -> p.tokenizer.next(CRLF), RequestParser::parseHeader),
        BODY(p -> p.tokenizer.next(p.contentLength), RequestParser::parseBody),
        CHUNK_SIZE(p -> p.tokenizer.next(CRLF), RequestParser::parseChunkSize),
        CHUNK_DATA(p -> p.tokenizer.next(p.chunkSize), RequestParser::parseChunkData),
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

    private State state = State.METHOD;
    private int contentLength;
    private int chunkSize;
    private ByteMerger chunks = new ByteMerger();

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

    private void parseMethod(byte[] token) {
        method = new String(token);
        state = State.URI;
    }

    private void parseUri(byte[] token) {
        uri = new String(token);
        state = State.VERSION;
    }

    private void parseVersion(byte[] token) {
        version = new String(token);
        state = State.HEADER;
    }

    private void parseHeader(byte[] token) {
        if (token.length == 0) { // CR-LF on own line, end of headers
            if (hasMultipleTransferLengths()) {
                throw new IllegalStateException("multiple message lengths");
            }
            Integer contentLength = findContentLength();
            if (contentLength == null) {
                if (hasChunkedEncodingHeader()) {
                    state = State.CHUNK_SIZE;
                } else {
                    state = State.DONE;
                    this.contentLength = 0;
                }
            } else {
                this.contentLength = contentLength;
                state = State.BODY;
            }
        } else {
            headers.add(parseHeaderLine(token));
        }
    }

    private static Header parseHeaderLine(byte[] line) {
        int colonIndex = indexOfColon(line);
        if (colonIndex <= 0) {
            throw new IllegalStateException("malformed header line");
        }
        int spaceIndex = colonIndex + 1;
        while (spaceIndex < line.length && line[spaceIndex] == ' ') { // advance beyond variable-length space prefix
            spaceIndex++;
        }
        return new Header(
                new String(line, 0, colonIndex),
                new String(line, spaceIndex, line.length - spaceIndex));
    }

    private static int indexOfColon(byte[] line) {
        for (int i = 0; i < line.length; i++) {
            if (line[i] == ':') {
                return i;
            }
        }
        return -1;
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
        body = chunks.merge();
        state = State.DONE;
    }

    private void parseBody(byte[] token) {
        body = token;
        state = State.DONE;
    }

    private boolean hasMultipleTransferLengths() {
        int count = 0;
        for (Header header : headers) {
            if (header.name().equalsIgnoreCase(HEADER_CONTENT_LENGTH) || header.name().equalsIgnoreCase(HEADER_TRANSFER_ENCODING)) {
                count++;
            }
        }
        return count > 1;
    }

    private Integer findContentLength() {
        try {
            for (Header header : headers) {
                if (header.name().equalsIgnoreCase(HEADER_CONTENT_LENGTH)) {
                    return Integer.parseInt(header.value());
                }
            }
            return null;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("invalid content-length header value");
        }
    }

    private boolean hasChunkedEncodingHeader() {
        for (Header header : headers) {
            if (header.name().equalsIgnoreCase(HEADER_TRANSFER_ENCODING) && header.value().equalsIgnoreCase(CHUNKED)) {
                return true;
            }
        }
        return false;
    }

}
