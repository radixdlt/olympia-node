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

package com.radixdlt.middleware2;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.hash.HashCode;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.atom.Atom;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class AtomTest {

	@Test
	public void equalsContract() {
		EqualsVerifier
			.forClass(Atom.class)
			// Only AID is compared.
			.withIgnoredFields("message", "instructions", "signatures")
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	private static AtomBuilder createApiAtom() {
		AtomBuilder atom = new AtomBuilder();
		// add a particle to ensure atom is valid and has at least one shard
		atom.addParticleGroupWith(makeParticle("Hello"), Spin.UP);
		return atom;
	}

	@Test
	public void testGetters() throws Exception {
		AtomBuilder atom = createApiAtom();
		var hash = atom.computeHashToSign();
		final Atom clientAtom = atom.buildAtom();
		assertThat(AID.from(hash.asBytes())).isEqualTo(clientAtom.getAID());
		assertThat(atom.getMessage()).isEqualTo(clientAtom.getMessage());
		assertThat(clientAtom.getCMInstruction()).isNotNull();
	}

    private static UniqueParticle makeParticle(String message) {
    	final var kp = ECKeyPair.generateNew();
    	final var address = new RadixAddress((byte) 0, kp.getPublicKey());
    	return new UniqueParticle(message, address, 0);
    }
}