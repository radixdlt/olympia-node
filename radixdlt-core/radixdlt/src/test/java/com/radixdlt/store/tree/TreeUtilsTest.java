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
}
