package microhttp;

import java.nio.ByteBuffer;
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

    void serialize(String version, List<Header> headers, ByteBuffer writeBuffer) {
        writeBuffer.put(version.getBytes());
        writeBuffer.put(SPACE);
        writeBuffer.put(Integer.toString(status).getBytes());
        writeBuffer.put(SPACE);
        writeBuffer.put(reason.getBytes());
        writeBuffer.put(CRLF);
        for (List<Header> list : List.of(headers, this.headers)) {
            for (Header header : list) {
                writeBuffer.put(header.name().getBytes());
                writeBuffer.put(COLON_SPACE);
                writeBuffer.put(header.value().getBytes());
                writeBuffer.put(CRLF);
            }
        }
        writeBuffer.put(CRLF);
        writeBuffer.put(body);
    }

}
