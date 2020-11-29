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

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.berkeley.BerkeleySafetyStateStore;
import java.util.Optional;

/**
 * Manages consensus recovery on restarts
 */
public class RecoveryModule extends AbstractModule {
	@Provides
	@Singleton
	private SafetyState safetyState(EpochChange initialEpoch, BerkeleySafetyStateStore berkeleySafetyStore) {
		return berkeleySafetyStore.get().flatMap(p -> {
			if (p.getFirst() > initialEpoch.getEpoch()) {
				throw new IllegalStateException("Last vote is in a future epoch.");
			}

			if (p.getFirst() == initialEpoch.getEpoch()) {
				return Optional.of(p.getSecond());
			} else {
				return Optional.empty();
			}
		}).orElse(new SafetyState());
	}

	@Provides
	@Singleton
	private VerifiedVertexStoreState vertexStoreState(
		@LastEpochProof VerifiedLedgerHeaderAndProof lastEpochProof,
		PersistentVertexStore persistentVertexStore,
		Hasher hasher
	) {
		return persistentVertexStore.loadLastState()
			.map(serializedVertexStoreState -> {
				UnverifiedVertex root = serializedVertexStoreState.getRoot();
				HashCode rootVertexId = hasher.hash(root);
				VerifiedVertex verifiedRoot = new VerifiedVertex(root, rootVertexId);

				ImmutableList<VerifiedVertex> vertices = serializedVertexStoreState.getVertices().stream()
					.map(v -> new VerifiedVertex(v, hasher.hash(v)))
					.collect(ImmutableList.toImmutableList());

				return VerifiedVertexStoreState.create(
					serializedVertexStoreState.getHighQC(),
					verifiedRoot,
					vertices
				);
			})
			.orElseGet(() -> {
				UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(lastEpochProof.getRaw());
				VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
				LedgerHeader nextLedgerHeader = LedgerHeader.create(
					lastEpochProof.getEpoch() + 1,
					View.genesis(),
					lastEpochProof.getAccumulatorState(),
					lastEpochProof.timestamp()
				);
				QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
				return VerifiedVertexStoreState.create(HighQC.from(genesisQC), verifiedGenesisVertex);
			});
	}

}
