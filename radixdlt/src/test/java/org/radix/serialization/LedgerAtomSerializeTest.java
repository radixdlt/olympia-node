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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.LedgerAtom.LedgerAtomConversionException;
import org.junit.Test;

public class LedgerAtomSerializeTest extends SerializeObject<LedgerAtom> {
	public LedgerAtomSerializeTest() {
		super(LedgerAtom.class, LedgerAtomSerializeTest::get);
	}

	private static Atom createApiAtom() {
		RadixAddress address = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		Atom atom = new Atom();
		// add a particle to ensure atom is valid and has at least one shard
		atom.addParticleGroupWith(new MessageParticle(address, address, "Hello".getBytes()), Spin.UP);
		return atom;
	}

	private static LedgerAtom get(Atom atom) {
		final LedgerAtom ledgerAtom;
		try {
			ledgerAtom = LedgerAtom.convertFromApiAtom(atom);
		} catch (LedgerAtomConversionException e) {
			throw new IllegalStateException();
		}

		return ledgerAtom;
	}

	private static LedgerAtom get() {
		return get(createApiAtom());
	}

}
