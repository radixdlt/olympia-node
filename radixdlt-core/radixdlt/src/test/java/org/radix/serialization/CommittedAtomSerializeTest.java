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

package org.radix.serialization;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.*;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.statecomputer.CommittedAtom;

public class CommittedAtomSerializeTest extends SerializeObject<CommittedAtom> {

	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	public CommittedAtomSerializeTest() {
		super(CommittedAtom.class, CommittedAtomSerializeTest::get);
	}

	private static Atom createApiAtom() {
		final var address = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		final var rri = RRI.of(address, "test");

		final var atom = new Atom();
		// add a particle to ensure atom is valid and has at least one shard
		atom.addParticleGroupWith(new RRIParticle(rri), Spin.DOWN);
		return atom;
	}

	private static CommittedAtom get(Atom atom) {
		final ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom, hasher);

		LedgerHeader ledgerHeader = LedgerHeader.genesis(HashUtils.zero256(), null);
		VerifiedLedgerHeaderAndProof proof = new VerifiedLedgerHeaderAndProof(
			BFTHeader.ofGenesisAncestor(ledgerHeader),
			BFTHeader.ofGenesisAncestor(ledgerHeader),
			0L, HashUtils.random256(), ledgerHeader,
			new TimestampedECDSASignatures()
		);

		return new CommittedAtom(clientAtom, 0L, proof);
	}

	private static CommittedAtom get() {
		return get(createApiAtom());
	}

}
