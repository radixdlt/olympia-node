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
import com.radixdlt.consensus.Command;
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
import com.radixdlt.atom.Atom;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RegisteredValidators;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.statecomputer.ValidatorSetBuilder;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.store.AtomIndex;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.store.berkeley.SerializedVertexStoreState;
import com.radixdlt.sync.CommittedReader;

import java.util.List;
import java.util.Optional;

/**
 * Recovery for ledger
 */
public final class LedgerRecoveryModule extends AbstractModule {
	// TODO: Refactor genesis store method
	private static void storeGenesis(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		List<Atom> genesisAtoms,
		ValidatorSetBuilder validatorSetBuilder,
		Serialization serialization,
		LedgerAccumulator ledgerAccumulator
	) throws RadixEngineException {
		var branch = radixEngine.transientBranch();
		branch.execute(genesisAtoms, PermissionLevel.SYSTEM);
		final var genesisValidatorSet = validatorSetBuilder.buildValidatorSet(
			branch.getComputedState(RegisteredValidators.class),
			branch.getComputedState(Stakes.class)
		);
		radixEngine.deleteBranches();

		AccumulatorState accumulatorState = null;

		for (var genesisAtom : genesisAtoms) {
			var payload = serialization.toDson(genesisAtom, DsonOutput.Output.ALL);
			var command = new Command(payload);
			if (accumulatorState == null) {
				accumulatorState = new AccumulatorState(0, command.getId().asHashCode());
			} else {
				accumulatorState = ledgerAccumulator.accumulate(accumulatorState, command.getId().asHashCode());
			}
		}

		var genesisProof = LedgerProof.genesis(
			accumulatorState,
			genesisValidatorSet
		);
		if (!genesisProof.isEndOfEpoch()) {
			throw new IllegalStateException("Genesis must be end of epoch");
		}

		radixEngine.execute(genesisAtoms, LedgerAndBFTProof.create(genesisProof), PermissionLevel.SYSTEM);
	}

	@Provides
	@Singleton
	@LastStoredProof
	LedgerProof lastStoredProof(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		AtomIndex atomIndex,
		CommittedReader committedReader,
		@Genesis List<Atom> genesisAtoms,
		Serialization serialization,
		ValidatorSetBuilder validatorSetBuilder,
		LedgerAccumulator ledgerAccumulator
	) throws RadixEngineException {
		Command cmd = new Command(DefaultSerialization.getInstance().toDson(genesisAtoms.get(0), DsonOutput.Output.ALL));
		if (!atomIndex.contains(cmd.getId())) {
			storeGenesis(radixEngine, genesisAtoms, validatorSetBuilder, serialization, ledgerAccumulator);
		}

		return committedReader.getLastProof().orElseThrow();
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
			serializedVertexStoreState.getHighestTC()
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
		return VerifiedVertexStoreState.create(HighQC.from(genesisQC), verifiedGenesisVertex, Optional.empty());
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
