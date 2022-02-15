package org.microhttp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP request entity.
 */
public final class Request {

    private final String method;
    private final String uri;
    private final String version;
    private final List<Header> headers;
    private final byte[] body;

    public Request(
            String method,
            String uri,
            String version,
            List<Header> headers,
            byte[] body) {
        this.method = method;
        this.uri = uri;
        this.version = version;
        this.headers = headers;
        this.body = body;
    }

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

    public String method() {
        return method;
    }

    public String uri() {
        return uri;
    }

    public String version() {
        return version;
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
        Request that = (Request) obj;
        return Objects.equals(this.method, that.method) &&
                Objects.equals(this.uri, that.uri) &&
                Objects.equals(this.version, that.version) &&
                Objects.equals(this.headers, that.headers) &&
                Objects.equals(this.body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, uri, version, headers, body);
    }

    @Override
    public String toString() {
        return "Request[" +
                "method=" + method + ", " +
                "uri=" + uri + ", " +
                "version=" + version + ", " +
                "headers=" + headers + ", " +
                "body=" + body + ']';
    }

}
