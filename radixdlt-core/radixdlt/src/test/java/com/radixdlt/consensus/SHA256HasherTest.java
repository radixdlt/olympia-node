package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.atom.Atom;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SHA256HasherTest {

    private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

    @Test
    public void hasher_test_atom() {
        Atom atom = new Atom();
        HashCode hash = hasher.hash(atom);
        assertIsNotRawDSON(hash);
        String hashHex = hash.toString();
        assertEquals("fd208580f27bd833992afa0369d43f001ac1599743a22f7b8111f9544712f47e", hashHex);
    }

    @Test
    public void hasher_test_particle() {
        RadixAddress address = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
        RRI rri = RRI.of(address, "FOOBAR");
        RRIParticle particle = new RRIParticle(rri);
        HashCode hash = hasher.hash(particle);
        assertIsNotRawDSON(hash);
        String hashHex = hash.toString();
        assertEquals("ea6096c4c181078114de3f609a97e7b97b8ff5f182e74e5168d68bc2a1ff702f", hashHex);
    }

    private void assertIsNotRawDSON(HashCode hash) {
        String hashHex = hash.toString();
        // CBOR/DSON encoding of an object starts with "bf" and ends with "ff", so we are here making
        // sure that Hash of the object is not just the DSON output, but rather a 256 bit hash digest of it.
        // the probability of 'accidentally' getting getting these prefixes and suffixes anyway is minimal (1/2^16)
        // for any DSON bytes as argument.
        assertFalse(hashHex.startsWith("bf") && hashHex.endsWith("ff"));
    }
}
