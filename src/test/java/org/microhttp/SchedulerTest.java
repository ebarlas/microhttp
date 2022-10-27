package org.microhttp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

public class SchedulerTest {

    @Test
    public void scheduleAndTakeExpired() {
        TestClock clock = new TestClock();
        Scheduler scheduler = new Scheduler(clock);
        Runnable first = () -> {};
        Runnable second = () -> {};
        scheduler.schedule(first, Duration.ZERO);
        scheduler.schedule(second, Duration.ofNanos(1));
        Assertions.assertEquals(List.of(first), scheduler.expired());
        Assertions.assertEquals(1, scheduler.size());
        Assertions.assertEquals(List.of(), scheduler.expired());
        Assertions.assertEquals(1, scheduler.size());
        clock.time = 1;
        Assertions.assertEquals(List.of(second), scheduler.expired());
        Assertions.assertEquals(0, scheduler.size());
    }

    @Test
    public void cancelAndReschedule() {
        TestClock clock = new TestClock();
        Scheduler scheduler = new Scheduler(clock);
        Runnable first = () -> {};
        Runnable second = () -> {};
        Cancellable stOne = scheduler.schedule(first, Duration.ofNanos(1));
        Cancellable stTwo = scheduler.schedule(second, Duration.ofNanos(1));
        Assertions.assertEquals(List.of(), scheduler.expired());
        Assertions.assertEquals(2, scheduler.size());
        stOne.cancel();
        Assertions.assertEquals(1, scheduler.size());
    }

    static class TestClock implements Clock {
        long time;

        @Override
        public long nanoTime() {
            return time;
        }
    }

}
