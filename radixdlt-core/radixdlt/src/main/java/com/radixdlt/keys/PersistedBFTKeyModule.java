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

package com.radixdlt.keys;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.properties.RuntimeProperties;

/**
 * Configures the key to be used for signing things as a BFT validator.
 */
public final class PersistedBFTKeyModule extends AbstractModule {
	@Override
	public void configure() {
		bind(HashSigner.class).annotatedWith(Names.named("RadixEngine")).to(HashSigner.class);
	}

	@Provides
	@Singleton
	PersistedBFTKeyManager bftKeyManager(RuntimeProperties properties) {
		var nodeKeyPath = properties.get("node.key.path", "node.ks");

		return new PersistedBFTKeyManager(nodeKeyPath);
	}

	@Provides
	@Self
	ECPublicKey key(@Self BFTNode bftNode) {
		return bftNode.getKey();
	}

	@Provides
	@Self
	ECKeyPair ecKeyPair(PersistedBFTKeyManager keyManager) {
		return keyManager.getKeyPair();
	}

	@Provides
	@Self
	BFTNode bftNode(PersistedBFTKeyManager bftKeyManager) {
		return bftKeyManager.self();
	}

	@Provides
	@Singleton
	HashSigner hashSigner(PersistedBFTKeyManager bftKeyManager, SystemCounters counters) {
		return hash -> {
			counters.increment(CounterType.SIGNATURES_SIGNED);
			return bftKeyManager.sign(hash);
		};
	}
}
