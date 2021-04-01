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

package com.radixdlt.integration.distributed.simulation.application;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.consensus.Command;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;

/**
 * Generates a new unique rri consumer command. Because new addresses are used
 * on every call, the command should never fail when executed on a radix engine.
 */
public class RadixEngineUniqueGenerator implements CommandGenerator {
	@Override
	public Command nextCommand() {
		var keyPair = ECKeyPair.generateNew();
		var address = new RadixAddress((byte) 0, keyPair.getPublicKey());
		try {
			var atom = TxBuilder.newBuilder(address)
				.mutex("test")
				.signAndBuild(keyPair::sign);
			final byte[] payload = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
			return new Command(payload);
		} catch (TxBuilderException e) {
			throw new RuntimeException(e);
		}
	}
}
