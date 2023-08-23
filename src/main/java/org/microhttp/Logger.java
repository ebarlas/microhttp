package org.microhttp;

/**
 * Simple logging abstraction that operates on {@link LogEntry} instances.
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

    static Logger noop() {
        return NoopLogger.INSTANCE;
    }

}
