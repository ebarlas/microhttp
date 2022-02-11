package microhttp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

class ByteTokenizerTest {

    @Test
    void compact() {
        ByteTokenizer tokenizer = new ByteTokenizer();
        tokenizer.add(ByteBuffer.wrap("hello world".getBytes()));
        Assertions.assertArrayEquals("hello".getBytes(), tokenizer.next(" ".getBytes()));
        Assertions.assertEquals(11, tokenizer.capacity());
        Assertions.assertEquals(5, tokenizer.remaining());
        tokenizer.compact();
        Assertions.assertEquals(5, tokenizer.capacity());
        Assertions.assertEquals(5, tokenizer.remaining());
        Assertions.assertArrayEquals("world".getBytes(), tokenizer.next(5));
        tokenizer.compact();
        Assertions.assertEquals(0, tokenizer.capacity());
        Assertions.assertEquals(0, tokenizer.remaining());
    }

    @Test
    void expandingArray() {
        ByteTokenizer tokenizer = new ByteTokenizer();
        Assertions.assertEquals(0, tokenizer.remaining());
        Assertions.assertEquals(0, tokenizer.size());
        Assertions.assertEquals(0, tokenizer.capacity());
        addAndAssert(tokenizer, "a", 1, 1, 1);
        addAndAssert(tokenizer, "b", 2, 2, 2);
        addAndAssert(tokenizer, "c", 3, 3, 4);
        addAndAssert(tokenizer, "xxxxxxx", 10, 10, 10);
    }

    static void addAndAssert(ByteTokenizer tokenizer, String data, int remaining, int size, int capacity) {
        tokenizer.add(ByteBuffer.wrap(data.getBytes()));
        Assertions.assertEquals(remaining, tokenizer.remaining());
        Assertions.assertEquals(size, tokenizer.size());
        Assertions.assertEquals(capacity, tokenizer.capacity());
    }

    @Test
    void noBytes() {
        ByteTokenizer tokenizer = new ByteTokenizer();
        Assertions.assertEquals(0, tokenizer.remaining());
        Assertions.assertNull(tokenizer.next(1));
        Assertions.assertNull(tokenizer.next(" ".getBytes()));
    }

    @Test
    void nextWithLength() {
        ByteTokenizer tokenizer = new ByteTokenizer();
        tokenizer.add(ByteBuffer.wrap("hello".getBytes()));
        Assertions.assertArrayEquals("h".getBytes(), tokenizer.next(1));
        Assertions.assertArrayEquals("ello".getBytes(), tokenizer.next(4));
        Assertions.assertNull(tokenizer.next(1));
    }

    @Test
    void nextWithDelimiter() {
        ByteTokenizer tokenizer = new ByteTokenizer();
        tokenizer.add(ByteBuffer.wrap("hello".getBytes()));
        tokenizer.add(ByteBuffer.wrap(" ".getBytes()));
        tokenizer.add(ByteBuffer.wrap("world".getBytes()));
        tokenizer.add(ByteBuffer.wrap("\r\n".getBytes()));
        Assertions.assertArrayEquals("hello".getBytes(), tokenizer.next(" ".getBytes()));
        Assertions.assertNull(tokenizer.next(" ".getBytes()));
        Assertions.assertArrayEquals("world".getBytes(), tokenizer.next("\r\n".getBytes()));
        Assertions.assertNull(tokenizer.next("\r\n".getBytes()));
    }

}
