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

package com.radixdlt.consensus.bft;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;

import java.util.Objects;

/**
 * A node in a BFT network which can run BFT validation
 *
 * TODO: turn this into an interface so that an ECPublicKey is not required
 * TODO: Serialization of BFT messages are currently what prevent this from happening
 */
public final class BFTNode {
	private final ECPublicKey key;
	private final String simpleName;

	private BFTNode(ECPublicKey key, String simpleName) {
		this.key = Objects.requireNonNull(key);
		this.simpleName = Objects.requireNonNull(simpleName);
	}

	public static BFTNode create(ECPublicKey key) {
		return new BFTNode(key, key.euid().toString().substring(0, 6));
	}

	public static BFTNode fromPublicKeyBytes(byte[] key) throws PublicKeyException {
		return create(ECPublicKey.fromBytes(key));
	}

	public static BFTNode random() {
		return create(ECKeyPair.generateNew().getPublicKey());
	}

	public ECPublicKey getKey() {
		return key;
	}

	@Override
	public int hashCode() {
		return Objects.hash(key);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof BFTNode)) {
			return  false;
		}

		BFTNode bftNodeId = (BFTNode) o;
		return Objects.equals(bftNodeId.key, this.key);
	}

	public String getSimpleName() {
		return simpleName;
	}

	@Override
	public String toString() {
		return simpleName;
	}
}
