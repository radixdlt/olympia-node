package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertEquals;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Collections;
import org.junit.Test;

public class AtomBuilderTest {
	@Test
	public void buildTransactionAtomWithPayload() {
		ECKeyPair ecKeyPair = mock(ECKeyPair.class);
		when(ecKeyPair.getUID()).thenReturn(new EUID(1));

		Consumable consumable = new Consumable(1, Collections.singleton(ecKeyPair), 0, new EUID(2));

		AtomBuilder atomBuilder = new AtomBuilder();
		UnsignedAtom atom = atomBuilder
			.type(TransactionAtom.class)
			.addParticle(consumable)
			.payload("Hello")
			.build();

		assertEquals(atom.getRawAtom().getClass(), TransactionAtom.class);
		assertEquals(atom.getRawAtom().getAsTransactionAtom().getPayload().toAscii(), "Hello");
	}

	@Test
	public void testMultipleAtomPayloadBuildsShouldCreateSameAtom() {
		AtomBuilder atomBuilder = new AtomBuilder()
			.type(TransactionAtom.class)
			.applicationId("Test")
			.payload("Hello")
			.addDestination(new EUID(1));

		UnsignedAtom atom1 = atomBuilder.build();
		UnsignedAtom atom2 = atomBuilder.build();

		assertEquals(atom1.getHash(), atom2.getHash());
	}
}