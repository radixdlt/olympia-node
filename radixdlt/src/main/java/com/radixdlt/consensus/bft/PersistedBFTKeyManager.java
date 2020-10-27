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

import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.CryptoException;
import com.radixdlt.keys.Keys;
import java.io.IOException;

/**
 * Manages a persisted key pair to be used for signing
 */
public final class PersistedBFTKeyManager {
	private final ECKeyPair ecKeyPair;

	public PersistedBFTKeyManager(String nodeKeyPath) {
		this.ecKeyPair = loadNodeKey(nodeKeyPath);
	}

	private static ECKeyPair loadNodeKey(String nodeKeyPath) {
		try {
			return Keys.readKey(nodeKeyPath, "node", "RADIX_NODE_KEYSTORE_PASSWORD", "RADIX_NODE_KEY_PASSWORD");
		} catch (IOException | CryptoException ex) {
			throw new IllegalStateException("while loading node key", ex);
		}
	}

	public ECDSASignature sign(byte[] hash) {
		return ecKeyPair.sign(hash);
	}

	public BFTNode self() {
		return BFTNode.create(ecKeyPair.getPublicKey());
	}
}
