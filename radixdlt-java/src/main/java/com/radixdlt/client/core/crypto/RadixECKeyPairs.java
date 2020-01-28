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

package com.radixdlt.client.core.crypto;

import org.radix.crypto.Hash;

import java.util.Arrays;
import java.util.Objects;

public class RadixECKeyPairs {

    static {
        // Make sure the BouncyCastle cryptographic implementation is properly
        // installed.
        ECKeyPairGenerator.install();
    }

    public static RadixECKeyPairs newInstance() {
        return new RadixECKeyPairs();
    }


    // Intentionally hidden constructor
    private RadixECKeyPairs() {
    }

    /**
     * Generates a new, deterministic {@code ECKeyPair} instance based on the
     * provided seed.
     *
     * @param seed The seed to use when deriving the key pair instance.
     * @return A key pair that corresponds to the provided seed.
     * @throws IllegalArgumentException if the seed is empty or a null pointer.
     */
    public ECKeyPair generateKeyPairFromSeed(byte[] seed) {
        Objects.requireNonNull(seed, "Seed must not be null");

        if (seed.length == 0) {
            throw new IllegalArgumentException("Seed must not be empty");
        }


        byte[] privateKey = Hash.hash("SHA-256", seed);

        if (privateKey.length != 32) {
            byte[] copy = new byte[32];

            if (privateKey.length > 32) {
                // Cut (note that this will limit the key space to something
                // smaller than what initially may have been intended).
                copy = Arrays.copyOfRange(privateKey, privateKey.length - 32, privateKey.length);
            } else {
                // Pad (note that this will enable a key space smaller than
                // the full size of the ECKeyPair space).
                System.arraycopy(privateKey, 0, copy, 32 - privateKey.length, privateKey.length);
            }

            privateKey = copy;
        }

        return new ECKeyPair(privateKey);
    }

}
