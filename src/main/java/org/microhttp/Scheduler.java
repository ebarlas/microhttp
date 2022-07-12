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
 * expired tasks. A {@link Cancellable} handle is returned to clients when a new task is scheduled.
 * That handle can be used to cancel a task.
 *
 * Scheduler is thread-safe. Separate threads can schedule and drain tasks.
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

    synchronized int size() {
        return tasks.size();
    }

    synchronized Cancellable schedule(Runnable task, Duration duration) {
        Task t = new Task(task, clock.nanoTime() + duration.toNanos(), counter++);
        tasks.add(t);
        return t;
    }

    synchronized List<Runnable> expired() {
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

    private synchronized void cancel(Task task) {
        tasks.remove(task);
    }

    class Task implements Cancellable {
        final Runnable task;
        final long time;
        final long id;

        Task(Runnable task, long time, long id) {
            this.task = task;
            this.time = time;
            this.id = id;
        }

        @Override
        public void cancel() {
            Scheduler.this.cancel(this);
        }
    }

}
