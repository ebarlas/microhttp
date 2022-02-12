package microhttp;

import java.util.List;
import java.util.Optional;

/**
 * HTTP request entity.
 */
public record Request(
        String method,
        String uri,
        String version,
        List<Header> headers,
        byte[] body) {

    public Optional<String> header(String name) {
        return headers.stream()
                .filter(h -> h.name().equalsIgnoreCase(name))
                .map(Header::value)
                .findFirst();
    }

    public boolean hasHeader(String name, String value) {
        return headers.stream()
                .filter(h -> h.name().equalsIgnoreCase(name))
                .map(Header::value)
                .anyMatch(v -> v.equalsIgnoreCase(value));
    }

}
