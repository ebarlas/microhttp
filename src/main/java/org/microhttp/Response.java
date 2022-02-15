package org.microhttp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Response {

    private final int status;
    private final String reason;
    private final List<Header> headers;
    private final byte[] body;

    public Response(
            int status,
            String reason,
            List<Header> headers,
            byte[] body) {
        this.status = status;
        this.reason = reason;
        this.headers = headers;
        this.body = body;
    }

    public boolean hasHeader(String name) {
        return headers.stream().anyMatch(h -> h.name().equalsIgnoreCase(name));
    }

    static final byte[] COLON_SPACE = ": ".getBytes();
    static final byte[] SPACE = " ".getBytes();
    static final byte[] CRLF = "\r\n".getBytes();

    byte[] serialize(String version, List<Header> headers) {
        List<byte[]> segments = new ArrayList<>();
        segments.add(version.getBytes());
        segments.add(SPACE);
        segments.add(Integer.toString(status).getBytes());
        segments.add(SPACE);
        segments.add(reason.getBytes());
        segments.add(CRLF);
        for (List<Header> list : Arrays.asList(headers, this.headers)) {
            for (Header header : list) {
                segments.add(header.name().getBytes());
                segments.add(COLON_SPACE);
                segments.add(header.value().getBytes());
                segments.add(CRLF);
            }
        }
        segments.add(CRLF);
        segments.add(body);
        return merge(segments);
    }

    private static byte[] merge(List<byte[]> segments) {
        int size = segments.stream().mapToInt(a -> a.length).sum();
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] segment : segments) {
            System.arraycopy(segment, 0, result, offset, segment.length);
            offset += segment.length;
        }
        return result;
    }

    public int status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public List<Header> headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        Response that = (Response) obj;
        return this.status == that.status &&
                Objects.equals(this.reason, that.reason) &&
                Objects.equals(this.headers, that.headers) &&
                Objects.equals(this.body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, reason, headers, body);
    }

    @Override
    public String toString() {
        return "Response[" +
                "status=" + status + ", " +
                "reason=" + reason + ", " +
                "headers=" + headers + ", " +
                "body=" + body + ']';
    }

}
