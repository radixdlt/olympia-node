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

package org.radix.integration.serialization;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.universe.Universe;
import org.junit.Test;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;
import org.radix.integration.RadixTest;

import static org.assertj.core.api.Assertions.assertThat;

public class AtomHashTest extends RadixTest {
	@Test
	public void testThatParticleSpinAffectsAtomHash() throws CryptoException {
		Universe universe = getUniverse();
		RRIParticle p = new RRIParticle(RRI.of(new RadixAddress((byte) universe.getMagic(), ECKeyPair.generateNew().getPublicKey()), "test"));
		Atom atom1 = new Atom();
		atom1.addParticleGroupWith(p, Spin.UP);

		Atom atom2 = new Atom();
		atom2.addParticleGroupWith(p, Spin.DOWN);

		assertThat(atom1.getHash()).isNotEqualTo(atom2.getHash());
	}
}
