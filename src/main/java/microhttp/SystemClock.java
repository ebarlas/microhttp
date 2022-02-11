package microhttp;

class SystemClock implements Clock {
    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}
