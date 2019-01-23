package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.util.Hash;
import okio.ByteString;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.Collections;

public class AtomTest {
	@Test
	public void testEmptyAtom() {
		Atom atom = new Atom(Collections.emptyList());
		assertTrue(atom.getDataParticles().isEmpty());
		assertTrue(atom.getConsumables(Spin.UP).isEmpty());
		assertTrue(atom.getConsumables(Spin.DOWN).isEmpty());
		assertEquals("bf6a73657269616c697a65721a001ed1516776657273696f6e1864ff", ByteString.of(atom.toDson()).hex());
		assertEquals("1b1cff72cb4f79d2eb50b5fb2777d65bebb5cad146e2006f25cde7a53445ffe7", atom.getHash().toHexString());
		assertEquals("1b1cff72cb4f79d2eb50b5fb2777d65b", atom.getHid().toHexString());
		assertEquals(new Long(0), atom.getTimestamp());
		assertEquals(atom, new Atom(Collections.emptyList()));
	}
}