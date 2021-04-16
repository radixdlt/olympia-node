/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.client.core.atoms;

import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;

/**
 * Temporary helper class
 */
public final class Atoms {
	private Atoms() {
		throw new IllegalStateException("cannot instantiate.");
	}

	public static AID atomIdOf(Atom atom) {
		var dson = Serialize.getInstance().toDson(atom, DsonOutput.Output.ALL);
		var firstHash = HashUtils.sha256(dson);
		var secondHash = HashUtils.sha256(firstHash.asBytes());
		return AID.from(secondHash.asBytes());
	}
}
