package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.atommodel.Atom;
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
        assertEquals("311964d6688530d47baba551393b9a51a5e6a22504133597e3f7c2af5f83a2ce", hashHex);
    }

    @Test
    public void hasher_test_particle() {
        RadixAddress address = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
        RRI rri = RRI.of(address, "FOOBAR");
        RRIParticle particle = new RRIParticle(rri);
        HashCode hash = hasher.hash(particle);
        assertIsNotRawDSON(hash);
        String hashHex = hash.toString();
        assertEquals("a29e3505d9736f4de2a576b2fee1b6a449e56f6b3cbaa86b8388e39a1557c53a", hashHex);
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
