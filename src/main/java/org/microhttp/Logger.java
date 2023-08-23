package org.microhttp;

/**
 * Simple logging abstraction that operates on {@link LogEntry} instances.
 *
 * @see NoopLogger for using a logger that is not enabled
 */
public interface Logger {

    /**
     * Indicates whether logger is active.
     */
    boolean enabled();

    /**
     * Creates a new log event consisting of multiple log entries.
     */
    void log(LogEntry... entries);

    /**
     * Creates a new log event consisting of an exception and multiple log entries.
     */
    void log(Exception e, LogEntry... entries);

}
