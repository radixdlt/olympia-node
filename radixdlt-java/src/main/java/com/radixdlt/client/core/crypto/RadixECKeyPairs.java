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
