package com.radixdlt.store.tree;

import org.junit.Assert;
import org.junit.Test;

public class TreeUtilsTest {

    @Test
    public void when_nibbles_length_is_even_it_must_be_prefixed_with_even_prefix_followed_by_0() {
        // given
        byte[] nibbles = {0, 15, 1, 12, 11, 8}; //0f1cb8
        int oddPrefix = 3;
        int evenPrefix = 2;

        // when
        byte[] actual = TreeUtils.applyPrefix(
                nibbles,
                oddPrefix,
                evenPrefix
        );

        // then
        var expected = new byte[] {(byte) evenPrefix, 0, 0, 15, 1, 12, 11, 8};
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void when_nibbles_length_is_odd_it_must_be_prefixed_with_odd_prefix() {
        // given
        byte[] nibbles = {15, 1, 12, 11, 8}; //0f1cb8
        int oddPrefix = 3;
        int evenPrefix = 2;

        // when
        byte[] actual = TreeUtils.applyPrefix(
                nibbles,
                oddPrefix,
                evenPrefix
        );

        // then
        var expected = new byte[] {(byte) oddPrefix, 15, 1, 12, 11, 8};
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void when_most_significant_nibble_is_always_zero__then_it_is_converted_correctly() {
        byte[] fromNibblesToBytes = TreeUtils.fromNibblesToBytes(new byte[]{0, 1, 0, 2});

        Assert.assertArrayEquals(new byte[] {1, 2}, fromNibblesToBytes);
    }

    @Test
    public void when_least_significant_nibble_is_always_zero__then_it_is_converted_correctly() {
        byte[] fromNibblesToBytes = TreeUtils.fromNibblesToBytes(new byte[]{1, 0, 2, 0});

        Assert.assertArrayEquals(new byte[] {16, 32}, fromNibblesToBytes);
    }

    @Test
    public void when_most_significant_nibble_is_never_zero__then_it_is_converted_correctly() {
        byte[] fromNibblesToBytes = TreeUtils.fromNibblesToBytes(new byte[]{1, 1, 2, 2});

        Assert.assertArrayEquals(new byte[] {17, 34}, fromNibblesToBytes);
    }

    @Test
    public void when_most_significant_nibble_is_zero_once_then_it_is_converted_correctly() {
        byte[] fromNibblesToBytes = TreeUtils.fromNibblesToBytes(new byte[]{1, 1, 0, 2});

        Assert.assertArrayEquals(new byte[] {17, 2}, fromNibblesToBytes);
    }

    @Test
    public void when_least_significant_nibble_is_zero_once_then_it_is_converted_correctly() {
        byte[] fromNibblesToBytes = TreeUtils.fromNibblesToBytes(new byte[]{1, 0, 2, 2});

        Assert.assertArrayEquals(new byte[] {16, 34}, fromNibblesToBytes);
    }

    @Test
    public void when_nibbles_array_is_null__then_null_pointer_exception_is_thrown() {
        Assert.assertThrows(
                NullPointerException.class,
                () -> TreeUtils.fromNibblesToBytes(null)
        );
    }

    @Test
    public void when_nibbles_array_has_odd_length__then_illegal_argument_exception_is_thrown() {
        Assert.assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.fromNibblesToBytes(new byte[]{0, 1, 2})
        );
    }
}
