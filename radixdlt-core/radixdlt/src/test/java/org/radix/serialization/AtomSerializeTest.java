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

import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atom.Atom;

public class AtomSerializeTest extends SerializeObject<Atom> {

	public AtomSerializeTest() {
		super(Atom.class, AtomSerializeTest::get);
	}

	private static Atom get() {
		final var address = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		final var rri = RRI.of(address, "test");

		final var atom = Atom.newBuilder();
		// add a particle to ensure atom is valid and has at least one shard
		atom.addParticleGroup(ParticleGroup.of(SpunParticle.down(new RRIParticle(rri))));
		return atom.buildAtom();
	}
}
