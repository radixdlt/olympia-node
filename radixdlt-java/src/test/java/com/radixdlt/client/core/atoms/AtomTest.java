package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class AtomTest {
	@Test
	public void testNullAtom() {
		Atom atom = new Atom(null, null, null, null, null, 0);
		assertNull(atom.getDataParticle());
		assertEquals(0, atom.getParticles().size());
		assertEquals(0, atom.getConsumables().size());
		assertEquals(0, atom.getConsumers().size());
		assertNotNull(atom.getHash());
		assertNotNull(atom.getHid());
		assertNotNull(atom.summary());
		assertNotNull(atom.consumableSummary());
		assertEquals(new Long(0), atom.getTimestamp());
		assertNotNull(atom.toString());

		assertEquals(atom, new Atom(null, null, null, null, null, 0));
	}
}