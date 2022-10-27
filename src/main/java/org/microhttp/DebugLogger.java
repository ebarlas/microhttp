package org.microhttp;

import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DebugLogger implements Logger {
    private final long epoch = System.nanoTime();

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void log(LogEntry... entries) {
        long uptime = Duration.ofNanos(System.nanoTime() - epoch).toMillis();
        String text = Stream.of(entries)
                .map(e -> e.key() + "=" + e.value())
                .collect(Collectors.joining(", ", "[" + uptime + "] ", ""));
        System.out.println(text);
    }

    @Override
    public void log(Exception e, LogEntry... entries) {
        e.printStackTrace();
        log(entries);
    }
}
