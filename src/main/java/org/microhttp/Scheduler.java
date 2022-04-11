package org.microhttp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Scheduler is a simple data structure for efficiently scheduling deferred tasks and draining
 * expired tasks. A {@link ScheduledTask} handle is returned to clients when a new task is scheduled.
 * That handle can be used to cancel or reschedule a task.
 */
class Scheduler {

    private final Clock clock;
    private final SortedSet<Task> tasks;
    private long counter;

    Scheduler() {
        this(new SystemClock());
    }

    Scheduler(Clock clock) {
        this.clock = clock;
        this.tasks = new TreeSet<>(Comparator.comparing((Task t) -> t.time).thenComparing(t -> t.id));
    }

    int size() {
        return tasks.size();
    }

    ScheduledTask schedule(Runnable task, Duration duration) {
        Task t = new Task(task, duration, clock.nanoTime() + duration.toNanos(), counter++);
        tasks.add(t);
        return t;
    }

    List<Runnable> expired() {
        long time = clock.nanoTime();
        List<Runnable> result = new ArrayList<>();
        Iterator<Task> it = tasks.iterator();
        Task item;
        while (it.hasNext() && (item = it.next()).time <= time) {
            result.add(item.task);
            it.remove();
        }
        return result;
    }

    private void cancel(Task task) {
        tasks.remove(task);
    }

    private ScheduledTask reschedule(Task task) {
        tasks.remove(task);
        return schedule(task.task, task.duration);
    }

    class Task implements ScheduledTask {
        final Runnable task;
        final Duration duration;
        final long time;
        final long id;

        Task(Runnable task, Duration duration, long time, long id) {
            this.task = task;
            this.duration = duration;
            this.time = time;
            this.id = id;
        }

        @Override
        public void cancel() {
            Scheduler.this.cancel(this);
        }

        @Override
        public ScheduledTask reschedule() {
            return Scheduler.this.reschedule(this);
        }
    }

}
