/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.statecomputer;

import static org.junit.Assert.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.middleware2.ClientAtom;
import java.math.BigInteger;
import org.junit.Before;
import org.junit.Test;

public class ClientAtomToBinaryConverterTest {

	private final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	private ClientAtomToBinaryConverter clientAtomToBinaryConverter;

	@Before
	public void setUp() {
		clientAtomToBinaryConverter = new ClientAtomToBinaryConverter(DefaultSerialization.getInstance());
	}

	@Test
	public void test_atom_content_transformation_to_byte_array_and_back() throws Exception {
		ECDSASignature ecSignature = new ECDSASignature(BigInteger.ONE, BigInteger.ONE);
		ECKeyPair key = ECKeyPair.generateNew();
		RadixAddress radixAddress = new RadixAddress((byte) 1, key.getPublicKey());
		RRI rri = RRI.of(radixAddress, "test");
		RRIParticle rriParticle = new RRIParticle(rri);

		ParticleGroup particleGroup = ParticleGroup.of(ImmutableList.of(SpunParticle.up(rriParticle)));
		Atom atom = new Atom(
			ImmutableList.of(particleGroup),
			ImmutableMap.of(EUID.ONE, ecSignature),
			"test message"
		);

		ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom, hasher);

		byte[] serializedAtom = clientAtomToBinaryConverter.toLedgerEntryContent(clientAtom);
		ClientAtom deserializedAtom = clientAtomToBinaryConverter.toAtom(serializedAtom);
		assertEquals(clientAtom, deserializedAtom);
	}

}