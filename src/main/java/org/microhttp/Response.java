package org.microhttp;

import java.util.List;

public record Response(
        int status,
        String reason,
        List<Header> headers,
        byte[] body) {

    public boolean hasHeader(String name) {
        for (Header header : headers) {
            if (header.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    static final byte[] COLON_SPACE = ": ".getBytes();
    static final byte[] SPACE = " ".getBytes();
    static final byte[] CRLF = "\r\n".getBytes();

    byte[] serialize(String version, List<Header> headers) {
        ByteMerger merger = new ByteMerger();
        merger.add(version.getBytes());
        merger.add(SPACE);
        merger.add(Integer.toString(status).getBytes());
        merger.add(SPACE);
        merger.add(reason.getBytes());
        merger.add(CRLF);
        appendHeaders(merger, headers);
        appendHeaders(merger, this.headers);
        merger.add(CRLF);
        merger.add(body);
        return merger.merge();
    }

    private static void appendHeaders(ByteMerger merger, List<Header> headers) {
        for (Header header : headers) {
            merger.add(header.name().getBytes());
            merger.add(COLON_SPACE);
            merger.add(header.value().getBytes());
            merger.add(CRLF);
        }
    }

}
