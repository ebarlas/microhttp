package org.microhttp;

import java.util.List;

/**
 * Request objects represent discrete HTTP requests with request line, headers, and body.
 * Request objects have no hidden references to the network protocol layer that produced them.
 */
public record Request(
        String method,
        String uri,
        String version,
        List<Header> headers,
        byte[] body) {

    public String header(String name) {
        for (Header header : headers) {
            if (header.name().equalsIgnoreCase(name)) {
                return header.value();
            }
        }
        return null;
    }

    public boolean hasHeader(String name, String value) {
        for (Header header : headers) {
            if (header.name().equalsIgnoreCase(name) && header.value().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

}
