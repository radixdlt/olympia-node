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

package com.radixdlt.recovery;

import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;

import java.util.Optional;

/**
 * Starting configuration for simulation/deterministic steady state tests.
 */
public class MockedRecoveryModule extends AbstractModule {

	private final HashCode genesisHash;

	public MockedRecoveryModule() {
		this(HashUtils.zero256());
	}

	public MockedRecoveryModule(HashCode genesisHash) {
		this.genesisHash = genesisHash;
	}

	@Provides
	private ViewUpdate view(BFTConfiguration configuration, ProposerElection proposerElection) {
		HighQC highQC = configuration.getVertexStoreState().getHighQC();
		View view = highQC.highestQC().getView().next();
		final BFTNode leader = proposerElection.getProposer(view);
		final BFTNode nextLeader = proposerElection.getProposer(view.next());

		return ViewUpdate.create(view, highQC, leader, nextLeader);
	}

	@Provides
	private BFTConfiguration configuration(
		@LastEpochProof LedgerProof proof,
		BFTValidatorSet validatorSet
	) {
		var accumulatorState = new AccumulatorState(0, genesisHash);
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(LedgerHeader.genesis(accumulatorState, validatorSet, 0));
		VerifiedVertex verifiedGenesis = new VerifiedVertex(genesis, genesisHash);
		LedgerHeader nextLedgerHeader = LedgerHeader.create(
			proof.getEpoch() + 1,
			View.genesis(),
			proof.getAccumulatorState(),
			proof.timestamp()
		);
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesis, nextLedgerHeader);
		return new BFTConfiguration(
			validatorSet,
			VerifiedVertexStoreState.create(HighQC.from(genesisQC), verifiedGenesis, Optional.empty())
		);
	}

	@Provides
	@LastEpochProof
	public LedgerProof lastEpochProof(BFTValidatorSet validatorSet) {
		var accumulatorState = new AccumulatorState(0, HashUtils.zero256());
		return LedgerProof.genesis(accumulatorState, validatorSet, 0);
	}

	@Provides
	@LastProof
	private LedgerProof lastProof(BFTConfiguration bftConfiguration) {
		return bftConfiguration.getVertexStoreState().getRootHeader();
	}
}
