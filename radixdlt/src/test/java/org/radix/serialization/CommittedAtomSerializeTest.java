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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.utils.UInt256;

public class CommittedAtomSerializeTest extends SerializeObject<CommittedAtom> {
	public CommittedAtomSerializeTest() {
		super(CommittedAtom.class, CommittedAtomSerializeTest::get);
	}

	private static Atom createApiAtom() {
		RadixAddress address = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		Atom atom = new Atom();
		// add a particle to ensure atom is valid and has at least one shard
		atom.addParticleGroupWith(new MessageParticle(address, address, "Hello".getBytes()), Spin.UP);
		return atom;
	}

	private static CommittedAtom get(Atom atom) {
		final ClientAtom clientAtom;
		try {
			clientAtom = ClientAtom.convertFromApiAtom(atom);
		} catch (LedgerAtomConversionException e) {
			throw new IllegalStateException();
		}

		return new CommittedAtom(clientAtom, VertexMetadata.ofGenesisAncestor(
			PreparedCommand.create(0, 0L, false)
		));
	}

	private static CommittedAtom get() {
		return get(createApiAtom());
	}

}
