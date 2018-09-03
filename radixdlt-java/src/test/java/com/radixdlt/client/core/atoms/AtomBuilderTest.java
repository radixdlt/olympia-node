package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertEquals;

import com.radixdlt.client.core.address.EUID;
import org.junit.Test;

public class AtomBuilderTest {
	@Test
	public void testMultipleAtomPayloadBuildsShouldCreateSameAtom() {
		AtomBuilder atomBuilder = new AtomBuilder()
			.setDataParticle(new DataParticle(new Payload("Hello".getBytes()), "Test"))
			.addDestination(new EUID(1));

		UnsignedAtom atom1 = atomBuilder.build(0);
		UnsignedAtom atom2 = atomBuilder.build(0);

		assertEquals(atom1.getHash(), atom2.getHash());
	}
}