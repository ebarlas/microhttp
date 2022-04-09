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

        final Function<RequestParser, ByteArraySlice> tokenSupplier;
        final BiConsumer<RequestParser, ByteArraySlice> tokenConsumer;

        State(Function<RequestParser, ByteArraySlice> tokenSupplier, BiConsumer<RequestParser, ByteArraySlice> tokenConsumer) {
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
            ByteArraySlice slice = state.tokenSupplier.apply(this);
            if (slice == null) {
                return false;
            }
            state.tokenConsumer.accept(this, slice);
        }
        return true;
    }

    Request request() {
        return new Request(method, uri, version, headers, body);
    }

    private void parseMethod(ByteArraySlice slice) {
        method = slice.encode();
        state = State.URI;
    }

    private void parseUri(ByteArraySlice slice) {
        uri = slice.encode();
        state = State.VERSION;
    }

    private void parseVersion(ByteArraySlice slice) {
        version = slice.encode();
        state = State.HEADER;
    }

    private void parseHeader(ByteArraySlice slice) {
        if (slice.length() == 0) { // CR-LF on own line, end of headers
            if (hasMultipleTransferLengths()) {
                throw new IllegalStateException("multiple message lengths");
            }
            Integer contentLength = findContentLength();
            if (contentLength == null) {
                if (hasChunkedEncodingHeader()) {
                    state = State.CHUNK_SIZE;
                } else {
                    state = State.DONE;
                }
            } else {
                this.contentLength = contentLength;
                state = State.BODY;
            }
        } else {
            headers.add(parseHeaderLine(slice));
        }
    }

    private static Header parseHeaderLine(ByteArraySlice slice) {
        int colonIndex = indexOfColon(slice);
        if (colonIndex <= slice.offset()) {
            throw new IllegalStateException("malformed header line");
        }
        int spaceIndex = colonIndex + 1;
        while (spaceIndex < slice.offset() + slice.length() && slice.array()[spaceIndex] == ' ') { // advance beyond variable-length space prefix
            spaceIndex++;
        }
        if (spaceIndex == slice.offset() + slice.length()) {
            throw new IllegalStateException("malformed header line");
        }
        return new Header(
                new String(slice.array(), slice.offset(), colonIndex - slice.offset()),
                new String(slice.array(), spaceIndex, slice.offset() + slice.length() - spaceIndex));
    }

    private static int indexOfColon(ByteArraySlice slice) {
        for (int i = slice.offset(); i < slice.offset() + slice.length(); i++) {
            if (slice.array()[i] == ':') {
                return i;
            }
        }
        return -1;
    }

    private void parseChunkSize(ByteArraySlice slice) {
        try {
            chunkSize = Integer.parseInt(slice.encode(), RADIX_HEX);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("invalid chunk size");
        }
        state = chunkSize == 0
                ? State.CHUNK_TRAILER
                : State.CHUNK_DATA;
    }

    private void parseChunkData(ByteArraySlice slice) {
        chunks.add(slice);
        state = State.CHUNK_DATA_END;
    }

    private void parseChunkDateEnd() {
        state = State.CHUNK_SIZE;
    }

    private void parseChunkTrailer() {
        body = chunks.merge();
        state = State.DONE;
    }

    private void parseBody(ByteArraySlice slice) {
        body = slice.extract();
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
