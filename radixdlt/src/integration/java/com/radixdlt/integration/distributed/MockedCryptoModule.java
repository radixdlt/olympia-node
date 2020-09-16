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
import com.google.inject.name.Named;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * For testing where verification and signing is skipped
 */
public class MockedCryptoModule extends AbstractModule {
	private static final HashFunction hashFunction = Hashing.goodFastHash(8 * 32);

	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
	}

	@Provides
	private HashVerifier hashVerifier() {
		return (pubKey, hash, sig) -> {
			byte[] concat = new byte[64];
			hash.copyTo(concat, 0);
			System.arraycopy(pubKey.getBytes(), 0, concat, 32, 32);
			long hashCode = hashFunction.hashBytes(concat).asLong();
			return sig.getR().longValue() == hashCode;
		};
	}

	@Provides
	private HashSigner hashSigner(@Named("self") BFTNode node) {
		return h -> {
			byte[] concat = new byte[64];
			System.arraycopy(h, 0, concat, 0, 32);
			System.arraycopy(node.getKey().getBytes(), 0, concat, 32, 32);
			long hashCode = hashFunction.hashBytes(concat).asLong();
			return new ECDSASignature(BigInteger.valueOf(hashCode), BigInteger.valueOf(hashCode));
		};
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
