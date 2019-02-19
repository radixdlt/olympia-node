package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.radixdlt.client.core.atoms.particles.Spin;
import org.junit.Test;
import org.radix.utils.primitives.Bytes;

import java.util.Collections;

public class AtomTest {
	@Test
	public void testEmptyAtom() {
		Atom atom = new Atom(Collections.emptyList());
		assertTrue(atom.getMessageParticles().isEmpty());
		assertTrue(atom.getOwnedTokensParticles(Spin.UP).isEmpty());
		assertTrue(atom.getOwnedTokensParticles(Spin.DOWN).isEmpty());
		/// The origin of these hashes are this library it self, commit: acbc5307cf5c9f7e1c30300f7438ef5dbc3bb629
		/// These hashes can be used as a reference for other Radix libraries, e.g. Swift.
		assertEquals("bf686d65746144617461bf6974696d657374616d706130ff6a73657269616c697a65721a001ed1516776657273696f6e1864ff", Bytes.toHexString(atom.toDson()));
		assertEquals("823e52dbdbcf7c91ea100f03b68ead290b2a2838251c70917368eb322caa70ff", atom.getHash().toHexString());
		assertEquals("823e52dbdbcf7c91ea100f03b68ead29", atom.getHid().toHexString());
		assertEquals(0, atom.getTimestamp());
		assertEquals(atom, new Atom(Collections.emptyList()));
	}
}