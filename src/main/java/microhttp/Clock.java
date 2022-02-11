package microhttp;

/**
 * Simple clock abstraction that produces clock times suitable for calculating time deltas.
 */
interface Clock {

    /**
     * Time now in nanoseconds.
     */
    long nanoTime();

}
