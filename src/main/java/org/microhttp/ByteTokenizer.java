package org.microhttp;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * ByteTokenizer is an expandable, first-in first-out byte array that supports tokenization.
 * Bytes are added at the tail and tokenization occurs at the head.
 */
class ByteTokenizer {
    private byte[] array = new byte[0];
    private int position;
    private int size;

    int position() {
        return position;
    }

    int size() {
        return size;
    }

    int capacity() {
        return array.length;
    }

    int remaining() {
        return size - position;
    }

    void compact() {
        array = Arrays.copyOfRange(array, position, size);
        size = size - position;
        position = 0;
    }

    void add(ByteBuffer buffer) {
        int bufferLen = buffer.remaining();
        if (array.length - size < bufferLen) {
            array = Arrays.copyOf(array, Math.max(size + bufferLen, array.length * 2));
        }
        buffer.get(array, size, bufferLen);
        size += bufferLen;
    }

    ByteArraySlice next(int length) {
        if (size - position < length) {
            return null;
        }
        ByteArraySlice result = new ByteArraySlice(array, position, length);
        position += length;
        return result;
    }

    ByteArraySlice next(byte[] delimiter) {
        int index = indexOf(delimiter);
        if (index < 0) {
            return null;
        }
        ByteArraySlice result = new ByteArraySlice(array, position, index - position);
        position = index + delimiter.length;
        return result;
    }

    private int indexOf(byte[] delimiter) {
        for (int i = position; i <= size - delimiter.length; i++) {
            if (Arrays.equals(delimiter, 0, delimiter.length, array, i, i + delimiter.length)) {
                return i;
            }
        }
        return -1;
    }

}
