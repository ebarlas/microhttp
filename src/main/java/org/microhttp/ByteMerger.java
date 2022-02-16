package org.microhttp;

import java.util.ArrayList;
import java.util.List;

/**
 * ByteMerger is a utility that accumulates a sequence of byte arrays and merges them into a single flat array upon request.
 */
class ByteMerger {

    private final List<byte[]> arrays = new ArrayList<>();

    void add(byte[] array) {
        arrays.add(array);
    }

    byte[] merge() {
        int size = sumOfLengths();
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    int sumOfLengths() {
        int sum = 0;
        for (byte[] array : arrays) {
            sum += array.length;
        }
        return sum;
    }

}
