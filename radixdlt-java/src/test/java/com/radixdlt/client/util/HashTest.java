/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.util;

import com.radixdlt.client.core.util.Hash;
import org.junit.Test;
import org.radix.utils.primitives.Bytes;

import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertEquals;

public class HashTest {

    @Test
    public void test_sha256_hash_as_reference_for_other_libraries()  {
        byte[] data = "Hello Radix".getBytes(StandardCharsets.UTF_8);
        byte[] singleHash = Hash.sha256(data);
        byte[] doubleHash = Hash.sha256(singleHash);

        // These hashes as just the result of running the sha256 once and output the values
        // These are then used as reference for other libraries, especially Swift which
        // lacks native Sha256 methods.
        assertEquals("374d9dc94c1252acf828cdfb94946cf808cb112aa9760a2e6216c14b4891f934", Bytes.toHexString(singleHash));
        assertEquals("fd6be8b4b12276857ac1b63594bf38c01327bd6e8ae0eb4b0c6e253563cc8cc7", Bytes.toHexString(doubleHash));
    }
}