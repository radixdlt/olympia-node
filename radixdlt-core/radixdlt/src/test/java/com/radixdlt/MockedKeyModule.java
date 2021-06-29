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
 *
 */

package com.radixdlt;

import com.google.common.hash.HashFunction;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.networks.Addressing;

import java.math.BigInteger;

public final class MockedKeyModule extends AbstractModule {
	@Provides
	@Self
	private String name(Addressing addressing, @Self BFTNode self) {
		return addressing.forValidators().of(self.getKey()).substring(0, 10);
	}

	@Provides
	private HashSigner hashSigner(
		@Self BFTNode node,
		SystemCounters counters,
		HashFunction hashFunction
	) {
		return h -> {
			var concat = new byte[64];
			System.arraycopy(h, 0, concat, 0, 32);
			System.arraycopy(node.getKey().getBytes(), 0, concat, 32, 32);

			var hashCode = hashFunction.hashBytes(concat).asLong();
			counters.increment(SystemCounters.CounterType.SIGNATURES_SIGNED);

			return ECDSASignature.create(BigInteger.valueOf(hashCode), BigInteger.valueOf(hashCode), 0);
		};
	}
}
