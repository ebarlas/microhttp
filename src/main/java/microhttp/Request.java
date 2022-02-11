package microhttp;

import java.util.List;

/**
 * HTTP request entity.
 */
public record Request(
        String method,
        String uri,
        String version,
        List<Header> headers,
        byte[] body) {

    public boolean hasHeader(String name, String value) {
        return headers.stream()
                .filter(h -> h.name().equalsIgnoreCase(name))
                .map(Header::value)
                .anyMatch(v -> v.equalsIgnoreCase(value));
    }

}
