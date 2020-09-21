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

package com.radixdlt.integration.distributed;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

/**
 * For testing where verification and signing is skipped
 */
public class MockedCryptoModule extends AbstractModule {
	private static final HashFunction hashFunction = Hashing.goodFastHash(8 * 32);

	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
		bind(HashVerifier.class).toInstance((pubKey, hash, sig) -> true);
		bind(HashSigner.class).toInstance(h -> new ECDSASignature());
	}

	@Provides
	private Hasher hasher(Serialization serialization) {
		return new Hasher() {
			@Override
			public Hash hash(Object o) {
				byte[] dson = serialization.toDson(o, Output.HASH);
				return this.hashBytes(dson);
			}

			@Override
			public Hash hashBytes(byte[] bytes) {
				byte[] hashCode = hashFunction.hashBytes(bytes).asBytes();
				return new Hash(hashCode, 0, 32);
			}
		};
	}
}
