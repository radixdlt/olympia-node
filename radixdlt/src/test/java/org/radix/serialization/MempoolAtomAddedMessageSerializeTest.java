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

import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.messages.MempoolAtomAddedMessage;

public class MempoolAtomAddedMessageSerializeTest extends SerializeMessageObject<MempoolAtomAddedMessage> {
	public MempoolAtomAddedMessageSerializeTest() {
		super(MempoolAtomAddedMessage.class, MempoolAtomAddedMessageSerializeTest::get);
	}

	private static MempoolAtomAddedMessage get() {
		Atom atom = new Atom();
		RadixAddress address = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		atom.addParticleGroupWith(new MessageParticle(address, address, "Hello".getBytes()), Spin.UP);
		final Command command = new Command(new byte[] {0, 1});
		return new MempoolAtomAddedMessage(1, command);
	}
}
