package com.radixdlt.middleware2.converters;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.Serialization;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class AtomToBinaryConverterTest {
	private AtomToBinaryConverter atomToBinaryConverter = new AtomToBinaryConverter(Serialization.getDefault());

	@Test
	public void test_atom_content_transformation_to_byte_array_and_back () throws CryptoException {
		ECSignature ecSignature = new ECSignature(BigInteger.ONE, BigInteger.ONE);
		ECKeyPair key = new ECKeyPair();
		RadixAddress radixAddress = new RadixAddress((byte)1, key.getPublicKey());
		RRI rri = RRI.of(radixAddress, "test");
		RRIParticle rriParticle = new RRIParticle(rri);

		ParticleGroup particleGroup = ParticleGroup.of(ImmutableList.of(SpunParticle.up(rriParticle)));
		Atom atom = new Atom(
			ImmutableList.of(particleGroup),
			ImmutableMap.of(EUID.ONE, ecSignature),
			ImmutableMap.of("timestamp", "0")
		);

		byte[] serializedAtom = atomToBinaryConverter.toLedgerEntryContent(atom);
		Atom deserializedAtom = atomToBinaryConverter.toAtom(serializedAtom);
		assertEquals(atom, deserializedAtom);
	}

}