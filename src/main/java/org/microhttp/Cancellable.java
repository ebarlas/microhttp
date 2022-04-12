package org.microhttp;

/**
 * Task handle returned by {@link Scheduler} that facilitates task cancellation.
 */
interface Cancellable {

    /**
     * Cancel scheduled task.
     */
    void cancel();

}
