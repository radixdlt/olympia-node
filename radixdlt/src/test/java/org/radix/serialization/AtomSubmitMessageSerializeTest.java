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

package org.radix.serialization;

import com.radixdlt.common.Atom;
import org.radix.atoms.messages.AtomSubmitMessage;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;

/**
 * Check serialization of AtomSubmitMessage
 */
public class AtomSubmitMessageSerializeTest extends SerializeMessageObject<AtomSubmitMessage> {
	public AtomSubmitMessageSerializeTest() {
		super(AtomSubmitMessage.class, AtomSubmitMessageSerializeTest::get);
	}

	private static AtomSubmitMessage get() {
		try {
			ECKeyPair key = new ECKeyPair();
			Atom atom = new Atom();
			atom.sign(key);
			return new AtomSubmitMessage(atom, 1);
		} catch (CryptoException e) {
			throw new IllegalStateException("Can't create ParticleConflict", e);
		}
	}
}
