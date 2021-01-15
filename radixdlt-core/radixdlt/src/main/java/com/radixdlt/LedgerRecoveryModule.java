package com.radixdlt;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;

import java.util.Optional;

public class LedgerRecoveryModule extends AbstractModule {
	@Provides
	@Singleton
	@LastProof
	VerifiedLedgerHeaderAndProof lastProof(
		CommittedAtomsStore store,
		VerifiedCommandsAndProof genesisCheckpoint, // TODO: remove once genesis creation resolved
		VerifiedVertexStoreState vertexStoreState
	) {
		VerifiedLedgerHeaderAndProof lastStoredProof = store.getLastVerifiedHeader()
				.orElse(genesisCheckpoint.getHeader());

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
		VerifiedCommandsAndProof genesisCheckpoint // TODO: remove once genesis creation resolved
	) {
		VerifiedLedgerHeaderAndProof lastStoredProof = store.getLastVerifiedHeader()
				.orElse(genesisCheckpoint.getHeader());

		if (lastStoredProof.isEndOfEpoch()) {
			return lastStoredProof;
		}
		return store.getEpochVerifiedHeader(lastStoredProof.getEpoch()).orElseThrow();
	}

	@Provides
	@Singleton
	private VerifiedVertexStoreState vertexStoreState(
		@LastEpochProof VerifiedLedgerHeaderAndProof lastEpochProof,
		BerkeleyLedgerEntryStore berkeleyLedgerEntryStore,
		Hasher hasher
	) {
		return berkeleyLedgerEntryStore.loadLastVertexStoreState()
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
