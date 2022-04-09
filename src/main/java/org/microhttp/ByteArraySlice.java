package org.microhttp;

import java.util.Arrays;

record ByteArraySlice(byte[] array, int offset, int length) {
    ByteArraySlice(byte[] array) {
        this(array, 0, array.length);
    }
    byte[] extract() {
        return Arrays.copyOfRange(array, offset, offset + length);
    }
    String encode() {
        return new String(array, offset, length);
    }
}
