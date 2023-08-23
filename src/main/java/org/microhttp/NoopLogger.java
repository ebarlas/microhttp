package org.microhttp;

/**
 * A logger that is disabled and does not log anything.
 */
public final class NoopLogger implements Logger {

    private static final NoopLogger INSTANCE = new NoopLogger();

    private NoopLogger() {
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void log(LogEntry... entries) {

    }

    @Override
    public void log(Exception e, LogEntry... entries) {

    }

    public static Logger instance() {
        return INSTANCE;
    }

}
