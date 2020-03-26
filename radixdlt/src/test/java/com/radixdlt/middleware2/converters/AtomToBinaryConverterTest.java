/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.Serialization;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class AtomToBinaryConverterTest {
	private AtomToBinaryConverter atomToBinaryConverter = new AtomToBinaryConverter(Serialization.getDefault());

	@Test
	public void test_atom_content_transformation_to_byte_array_and_back() throws CryptoException {
		ECDSASignature ecSignature = new ECDSASignature(BigInteger.ONE, BigInteger.ONE);
		ECKeyPair key = new ECKeyPair();
		RadixAddress radixAddress = new RadixAddress((byte) 1, key.getPublicKey());
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