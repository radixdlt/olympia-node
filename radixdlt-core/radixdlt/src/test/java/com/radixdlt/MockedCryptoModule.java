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

import com.radixdlt.consensus.bft.Self;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.google.common.hash.HashCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import java.math.BigInteger;

/**
 * For testing where verification and signing is skipped
 */
public class MockedCryptoModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger();
	private static final HashFunction hashFunction = Hashing.goodFastHash(8 * 32);

	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
	}

	@Provides
	private HashVerifier hashVerifier(SystemCounters counters) {
		return (pubKey, hash, sig) -> {
			byte[] concat = new byte[64];
			System.arraycopy(hash.asBytes(), 0, concat, 0, hash.asBytes().length);
			System.arraycopy(pubKey.getBytes(), 0, concat, 32, 32);
			long hashCode = hashFunction.hashBytes(concat).asLong();
			counters.increment(SystemCounters.CounterType.SIGNATURES_VERIFIED);
			return sig.getR().longValue() == hashCode;
		};
	}

	@Provides
	private HashSigner hashSigner(
		@Self BFTNode node,
		SystemCounters counters
	) {
		return h -> {
			byte[] concat = new byte[64];
			System.arraycopy(h, 0, concat, 0, 32);
			System.arraycopy(node.getKey().getBytes(), 0, concat, 32, 32);
			long hashCode = hashFunction.hashBytes(concat).asLong();
			counters.increment(SystemCounters.CounterType.SIGNATURES_SIGNED);
			return new ECDSASignature(BigInteger.valueOf(hashCode), BigInteger.valueOf(hashCode));
		};
	}

	@Provides
	private Hasher hasher(Serialization serialization, SystemCounters counters) {
		AtomicBoolean running = new AtomicBoolean(false);
		Hasher hasher = new Hasher() {
			@Override
			public int bytes() {
				return 32;
			}

			@Override
			public HashCode hash(Object o) {
				byte[] dson = timeWhinge("Serialization", () -> serialization.toDson(o, Output.HASH));
				return this.hashBytes(dson);
			}

			@Override
			public HashCode hashBytes(byte[] bytes) {
				byte[] hashCode = timeWhinge("Hashing", () -> hashFunction.hashBytes(bytes).asBytes());
				return HashCode.fromBytes(hashCode);
			}

			private <T> T timeWhinge(String what, Supplier<T> exec) {
				long start = System.nanoTime();
				T result = exec.get();
				long end = System.nanoTime();
				long durationMs = (end - start) / 1_000_000L;
				if (durationMs > 50) {
					log.warn("{} took {}ms", what, durationMs);
				}
				return result;
			}
		};

		// Make sure classes etc loaded, as first use seems to take some time
		Object dummyObject = new ECDSASignature(); // Arbitrary serializable class
		hasher.hash(dummyObject);
		running.set(true);

		return hasher;
	}
}
