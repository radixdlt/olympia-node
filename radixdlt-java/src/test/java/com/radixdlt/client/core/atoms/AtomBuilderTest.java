package com.radixdlt.client.core.atoms;

import static org.junit.Assert.*;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import java.math.BigInteger;
import java.util.Collections;
import org.junit.Test;

public class AtomBuilderTest {
	@Test
	public void buildTransactionAtomWithPayload() {
		// TODO: Mock these, can't do it easily at the moment because of Dson
		ECKeyPair ecKeyPair = ECKeyPairGenerator.generateKeyPair();
		Consumable consumable = new Consumable(1, Collections.singleton(ecKeyPair), 0, new EUID(BigInteger.valueOf(2L)));

		AtomBuilder atomBuilder = new AtomBuilder();
		UnsignedAtom atom = atomBuilder
			.type(TransactionAtom.class)
			.addParticle(consumable)
			.payload("Hello")
			.build()
		;

		assertEquals(atom.getRawAtom().getClass(), TransactionAtom.class);
		assertEquals(atom.getRawAtom().getAsTransactionAtom().getPayload().toAscii(), "Hello");
	}

	@Test
	public void testMultipleAtomPayloadBuildsShouldCreateSameAtom() {
		ECKeyPair ecKeyPair = ECKeyPairGenerator.generateKeyPair();
		AtomBuilder atomBuilder = new AtomBuilder()
			.type(ApplicationPayloadAtom.class)
			.applicationId("Test")
			.payload("Hello")
			.addProtector(ecKeyPair.getPublicKey())
			.addDestination(ecKeyPair.getUID());

		UnsignedAtom atom1 = atomBuilder.build();
		UnsignedAtom atom2 = atomBuilder.build();

		assertEquals(atom1.getHash(), atom2.getHash());
	}
}