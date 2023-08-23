package org.microhttp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class LoggerTest {

    @Test
    public void noopLogger() {
        Logger logger = Logger.noop();
        assertFalse(logger.enabled());
        assertSame(NoopLogger.INSTANCE, logger);
    }
}
