package org.microhttp;

final class NoopLogger implements Logger {

    static final NoopLogger INSTANCE = new NoopLogger();

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
}
