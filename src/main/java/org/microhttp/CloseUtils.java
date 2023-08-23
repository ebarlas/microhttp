package org.microhttp;

import java.io.Closeable;
import java.io.IOException;

class CloseUtils {

    static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignore) {
            // suppress
        }
    }
}
