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
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.RegisteredValidators;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.statecomputer.ValidatorSetBuilder;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.store.LedgerEntryStore;

import java.util.Optional;

/**
 * Recovery for ledger
 */
public final class LedgerRecoveryModule extends AbstractModule {
	@Provides
	@Singleton
	@LastStoredProof
	VerifiedLedgerHeaderAndProof lastStoredProof(
		RadixEngine<LedgerAtom> radixEngine,
		CommittedAtomsStore store,
		@Genesis Atom atom,
		Hasher hasher,
		Serialization serialization,
		ValidatorSetBuilder validatorSetBuilder
	) throws RadixEngineException {
		final ClientAtom genesisAtom = ClientAtom.convertFromApiAtom(atom, hasher);
		if (!store.containsAID(genesisAtom.getAID())) {
			RadixEngineBranch<LedgerAtom> branch = radixEngine.transientBranch();
			branch.checkAndStore(genesisAtom, PermissionLevel.SUPER_USER);
			final var genesisValidatorSet = validatorSetBuilder.buildValidatorSet(
				branch.getComputedState(RegisteredValidators.class),
				branch.getComputedState(Stakes.class)
			);
			radixEngine.deleteBranches();

			byte[] payload = serialization.toDson(genesisAtom, DsonOutput.Output.ALL);
			Command command = new Command(payload);
			VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(
				hasher.hash(command),
				genesisValidatorSet
			);
			if (!genesisLedgerHeader.isEndOfEpoch()) {
				throw new IllegalStateException("Genesis must be end of epoch");
			}
			CommittedAtom committedAtom = new CommittedAtom(
				genesisAtom,
				genesisLedgerHeader.getStateVersion(),
				genesisLedgerHeader
			);
			radixEngine.checkAndStore(committedAtom, PermissionLevel.SUPER_USER);
		}

		return store.getLastVerifiedHeader().orElseThrow();
	}

	@Provides
	@Singleton
	@LastProof
	VerifiedLedgerHeaderAndProof lastProof(
		VerifiedVertexStoreState vertexStoreState,
		@LastStoredProof VerifiedLedgerHeaderAndProof lastStoredProof
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
	VerifiedLedgerHeaderAndProof lastEpochProof(
		CommittedAtomsStore store,
		@LastStoredProof VerifiedLedgerHeaderAndProof lastStoredProof
	) {
		if (lastStoredProof.isEndOfEpoch()) {
			return lastStoredProof;
		}
		return store.getEpochVerifiedHeader(lastStoredProof.getEpoch()).orElseThrow();
	}

	@Provides
	@Singleton
	private VerifiedVertexStoreState vertexStoreState(
		@LastEpochProof VerifiedLedgerHeaderAndProof lastEpochProof,
		LedgerEntryStore ledgerEntryStore,
		Hasher hasher
	) {
		return ledgerEntryStore.loadLastVertexStoreState()
			.filter(vertexStoreState -> vertexStoreState.getHighQC().highestQC().getEpoch() == lastEpochProof.getEpoch() + 1)
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
					vertices,
					serializedVertexStoreState.getHighestTC()
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
				return VerifiedVertexStoreState.create(HighQC.from(genesisQC), verifiedGenesisVertex, Optional.empty());
			});
	}
}
