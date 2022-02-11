package microhttp;

import java.util.ArrayList;
import java.util.List;

public record Response(
        int status,
        String reason,
        List<Header> headers,
        byte[] body) {

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
        for (List<Header> list : List.of(headers, this.headers)) {
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

}
