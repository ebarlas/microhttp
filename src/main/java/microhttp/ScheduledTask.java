package microhttp;

/**
 * Task handle returned by {@link Scheduler} that facilitates task cancellation and rescheduling.
 */
interface ScheduledTask {

    /**
     * Cancel scheduled task.
     */
    void cancel();

    /**
     * Reschedule task with previously configured duration.
     */
    ScheduledTask reschedule();

}
