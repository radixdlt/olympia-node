package com.radixdlt.client.util;

import com.radixdlt.client.core.util.Hash;
import okio.ByteString;
import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static junit.framework.TestCase.assertEquals;

public class HashTest {

    @Test
    public void testDoubleSha256Hash() {
        byte[] data = new byte[0];
        try {
            data = "Hello Radix".getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        byte[] singleHash = Hash.sha256(data);
        byte[] doubleHash = Hash.sha256(singleHash);
        assertEquals("374d9dc94c1252acf828cdfb94946cf808cb112aa9760a2e6216c14b4891f934", ByteString.of(singleHash).hex());
        assertEquals("fd6be8b4b12276857ac1b63594bf38c01327bd6e8ae0eb4b0c6e253563cc8cc7", ByteString.of(doubleHash).hex());
    }
}