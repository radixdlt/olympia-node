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
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.store.berkeley.SerializedVertexStoreState;
import com.radixdlt.sync.CommittedReader;

import java.util.Optional;

/**
 * Recovery for ledger
 */
public final class LedgerRecoveryModule extends AbstractModule {
	@Provides
	@Singleton
	@LastStoredProof
	LedgerProof lastStoredProof(
		RadixEngine<LedgerAndBFTProof> radixEngine, // TODO: Remove
		CommittedReader committedReader,
		@Genesis VerifiedTxnsAndProof genesis,
		EventDispatcher<REOutput> committedDispatcher // FIXME: this is hack so client can get genesis
	) {
		return committedReader.getLastProof().orElseGet(() -> {
			var txns = genesis.getTxns();
			var proof = LedgerAndBFTProof.create(genesis.getProof());
			try {
				var parsed = radixEngine.execute(txns, proof, PermissionLevel.SYSTEM);
				committedDispatcher.dispatch(REOutput.create(parsed));
			} catch (RadixEngineException e) {
				throw new IllegalStateException("Error during node initialization", e);
			}

			return genesis.getProof();
		});
	}

	@Provides
	@Singleton
	@LastProof
	LedgerProof lastProof(
		VerifiedVertexStoreState vertexStoreState,
		@LastStoredProof LedgerProof lastStoredProof
	) {
		if (lastStoredProof.isEndOfEpoch()) {
			return vertexStoreState.getRootHeader();
		} else {
			return lastStoredProof;
		}
	}

	@Provides
	@Singleton
	@LastEpochProof
	LedgerProof lastEpochProof(
		CommittedReader committedReader,
		@LastStoredProof LedgerProof lastStoredProof
	) {
		if (lastStoredProof.isEndOfEpoch()) {
			return lastStoredProof;
		}
		return committedReader.getEpochProof(lastStoredProof.getEpoch()).orElseThrow();
	}

	private static VerifiedVertexStoreState serializedToVerifiedVertexStore(
		SerializedVertexStoreState serializedVertexStoreState,
		Hasher hasher
	) {
		var root = serializedVertexStoreState.getRoot();
		var rootVertexId = hasher.hash(root);
		var verifiedRoot = new VerifiedVertex(root, rootVertexId);

		var vertices = serializedVertexStoreState.getVertices().stream()
			.map(v -> new VerifiedVertex(v, hasher.hash(v)))
			.collect(ImmutableList.toImmutableList());

		return VerifiedVertexStoreState.create(
			serializedVertexStoreState.getHighQC(),
			verifiedRoot,
			vertices,
			serializedVertexStoreState.getHighestTC(),
			hasher
		);
	}

	private static VerifiedVertexStoreState epochProofToGenesisVertexStore(
		LedgerProof lastEpochProof,
		Hasher hasher
	) {
		var genesisVertex = UnverifiedVertex.createGenesis(lastEpochProof.getRaw());
		var verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
		var nextLedgerHeader = LedgerHeader.create(
			lastEpochProof.getEpoch() + 1,
			View.genesis(),
			lastEpochProof.getAccumulatorState(),
			lastEpochProof.timestamp()
		);
		var genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
		return VerifiedVertexStoreState.create(HighQC.from(genesisQC), verifiedGenesisVertex, Optional.empty(), hasher);
	}

	@Provides
	@Singleton
	private VerifiedVertexStoreState vertexStoreState(
		@LastEpochProof LedgerProof lastEpochProof,
		Optional<SerializedVertexStoreState> serializedVertexStoreState,
		Hasher hasher
	) {
		return serializedVertexStoreState
			.filter(vertexStoreState -> vertexStoreState.getHighQC().highestQC().getEpoch() == lastEpochProof.getEpoch() + 1)
			.map(state -> serializedToVerifiedVertexStore(state, hasher))
			.orElseGet(() -> epochProofToGenesisVertexStore(lastEpochProof, hasher));
	}
}
