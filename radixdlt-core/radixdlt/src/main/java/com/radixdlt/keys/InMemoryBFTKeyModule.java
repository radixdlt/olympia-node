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
 */

package com.radixdlt.keys;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;

/**
 * In memory Hash signing and identity handling
 */
public final class InMemoryBFTKeyModule extends AbstractModule {
	@Override
    public void configure() {
    	bind(HashSigner.class).annotatedWith(Names.named("RadixEngine")).to(HashSigner.class);
	}

	@Provides
	public HashSigner hashSigner(@Self ECKeyPair self) {
		return self::sign;
	}

	@Provides
	@Self
	RadixAddress radixAddress(@Named("magic") int magic, @Self BFTNode bftNode) {
		return new RadixAddress((byte) magic, bftNode.getKey());
	}

	@Provides
	@Self
	public BFTNode node(@Self ECKeyPair self) {
		return BFTNode.create(self.getPublicKey());
	}
}
