package org.microhttp;

import java.util.ArrayList;
import java.util.List;

/**
 * ByteMerger is a utility that accumulates a sequence of byte arrays and merges them into a single flat array upon request.
 */
class ByteMerger {

    private final List<ByteArraySlice> slices = new ArrayList<>();

    void add(byte[] array) {
        slices.add(new ByteArraySlice(array));
    }

    void add(ByteArraySlice slice) {
        slices.add(slice);
    }

    byte[] merge() {
        int size = sumOfLengths();
        byte[] result = new byte[size];
        int offset = 0;
        for (ByteArraySlice slice : slices) {
            System.arraycopy(slice.array(), slice.offset(), result, offset, slice.length());
            offset += slice.length();
        }
        return result;
    }

    int sumOfLengths() {
        int sum = 0;
        for (ByteArraySlice slice : slices) {
            sum += slice.length();
        }
        return sum;
    }

}
