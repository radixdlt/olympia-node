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

import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

/**
 * Module which maintains crypto primitives for consensus
 */
public final class CryptoModule extends AbstractModule {
	@Override
	protected void configure() {
		// Configuration
		bind(Serialization.class).toProvider(DefaultSerialization::getInstance);
	}


	@Provides
	Hasher hasher(Serialization serialization, SystemCounters counters) {
		return new Hasher() {
			private Sha256Hasher hasher = new Sha256Hasher(serialization);

			@Override
			public int bytes() {
				return 32;
			}

			@Override
			public HashCode hash(Object o) {
				// Call hashBytes to ensure counters incremented
				return this.hashBytes(serialization.toDson(o, Output.HASH));
			}

			@Override
			public HashCode hashBytes(byte[] bytes) {
				counters.add(CounterType.HASHED_BYTES, bytes.length);
				return hasher.hashBytes(bytes);
			}
		};
	}

	@Provides
	@Singleton
	HashVerifier hashVerifier(SystemCounters counters) {
		return (pubKey, hash, signature) -> {
			counters.increment(CounterType.SIGNATURES_VERIFIED);
			return pubKey.verify(hash, signature);
		};
	}

}
