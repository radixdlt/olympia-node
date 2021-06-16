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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Bytes;
import org.radix.Radix;
import org.radix.universe.system.LocalSystem;

public class LocalSystemSerializeTest extends SerializeValue<LocalSystem> {

	public LocalSystemSerializeTest() {
		super(LocalSystem.class, LocalSystemSerializeTest::get);
	}

	private static LocalSystem get() {
		try {
			ECKeyPair keyPair = ECKeyPair.fromPrivateKey(Bytes.fromHexString(Strings.repeat("deadbeef", 8)));
			return new LocalSystem(ImmutableMap::of, keyPair.getPublicKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalStateException("Failed to create keypair", e);
		}

	}
}
