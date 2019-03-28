package com.radixdlt.client.core.atoms;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.util.Hash;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AtomTest {
	@Test
	public void testEmptyAtom() {
		Atom atom = new Atom(Collections.emptyList(), 0L);

		/// The origin of these hashes are this library it self, commit: acbc5307cf5c9f7e1c30300f7438ef5dbc3bb629
		/// These hashes can be used as a reference for other Radix libraries, e.g. Swift.

		assertEquals("823e52dbdbcf7c91ea100f03b68ead29", atom.getHid().toHexString());
		assertEquals("Two empty atoms should equal", atom, new Atom(Collections.emptyList(), 0L));

		byte[] seed = Hash.sha256("Radix".getBytes(StandardCharsets.UTF_8));
		ECKeyPair ecKeyPair = new ECKeyPair(seed);
		ECSignature signature = ecKeyPair.sign(atom.getHash().toByteArray(), true, true);
		assertEquals("a16a6928b53af3a441f7248407e53f898ee7bda2911658d530306d3485d98f65", signature.getR().toString(16));
		assertEquals("7173a93790ce2b550ad484582c879bb7fea3113891b3ad9dca6a5b31471a580c", signature.getS().toString(16));
	}
}