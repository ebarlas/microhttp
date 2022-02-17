package org.microhttp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ByteMergerTest {

    @Test
    void addAndMerge() {
        ByteMerger merger = new ByteMerger();
        Assertions.assertArrayEquals(new byte[0], merger.merge());
        merger.add("hello".getBytes());
        merger.add(" ".getBytes());
        merger.add("world".getBytes());
        Assertions.assertArrayEquals("hello world".getBytes(), merger.merge());
    }

}
