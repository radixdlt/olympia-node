package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AtomTest {
	@Test
	public void testNullAtom() {
		Atom atom = new Atom(null, null, null, null, null, null, 0);
		assertTrue(atom.getDataParticles().isEmpty());
		assertTrue(atom.getConsumables().isEmpty());
		assertTrue(atom.getConsumers().isEmpty());
		assertNotNull(atom.getHash());
		assertNotNull(atom.getHid());
		assertNotNull(atom.summary());
		assertNotNull(atom.consumableSummary());
		assertEquals(new Long(0), atom.getTimestamp());
		assertNotNull(atom.toString());

		assertEquals(atom, new Atom(null, null, null, null, null, null, 0));
	}
}