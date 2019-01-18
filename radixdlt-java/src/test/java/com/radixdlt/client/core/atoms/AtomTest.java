package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.radixdlt.client.core.atoms.particles.Spin;
import org.junit.Test;

import java.util.Collections;

public class AtomTest {
	@Test
	public void testEmptyAtom() {
		Atom atom = new Atom(Collections.emptyList());
		assertTrue(atom.getDataParticles().isEmpty());
		assertTrue(atom.getConsumables(Spin.UP).isEmpty());
		assertTrue(atom.getConsumables(Spin.DOWN).isEmpty());
		assertNotNull(atom.getHash());
		assertNotNull(atom.getHid());
		assertEquals(new Long(0), atom.getTimestamp());
		assertNotNull(atom.toString());

		assertEquals(atom, new Atom(Collections.emptyList()));
	}
}