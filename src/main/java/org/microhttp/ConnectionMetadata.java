package org.microhttp;

/**
 * Metadata objects carry underlying network connection details.
 * @param id Network connection identifier that is unique over the lifetime of an event loop
 * @param ip Remote client IP address
 * @param port Remote client port
 */
public record ConnectionMetadata(String id, String ip, int port) {}
