package org.microhttp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

class TestLogger implements Logger {
    final List<List<LogEntry>> events = new ArrayList<>();

    synchronized boolean hasEventLog(String value) {
        return hasLog(ents -> ents.stream().anyMatch(e -> e.key().equals("event") && e.value().equals(value)));
    }

    synchronized long countEventLogs(String value) {
        return countLogs(ents -> ents.stream().anyMatch(e -> e.key().equals("event") && e.value().equals(value)));
    }

    synchronized boolean hasLog(Predicate<List<LogEntry>> predicate) {
        return events.stream().anyMatch(predicate);
    }

    synchronized long countLogs(Predicate<List<LogEntry>> predicate) {
        return events.stream().filter(predicate).count();
    }

    synchronized void clear() {
        events.clear();
    }

    @Override
    public synchronized boolean enabled() {
        return true;
    }

    @Override
    public synchronized void log(LogEntry... entries) {
        events.add(List.of(entries));
    }

    @Override
    public synchronized void log(Exception e, LogEntry... entries) {
        log(entries);
    }
}
