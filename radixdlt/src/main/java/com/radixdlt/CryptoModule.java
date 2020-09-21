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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

/**
 * Module which maintains crypto primitives for consensus
 */
public final class CryptoModule extends AbstractModule {
	@Override
	protected void configure() {
		// Configuration
		bind(HashVerifier.class).toInstance(ECPublicKey::verify);
	}

	@Provides
	Hasher hasher(Serialization serialization) {
		return new Hasher() {
			@Override
			public Hash hash(Object o) {
				return Hash.of(serialization.toDson(o, Output.HASH));
			}

			@Override
			public Hash hashBytes(byte[] bytes) {
				return Hash.of(bytes);
			}
		};
	}

	@Provides
	@Singleton
	HashSigner hashSigner(
		@Named("self") ECKeyPair selfKey
	) {
		return selfKey::sign;
	}
}
