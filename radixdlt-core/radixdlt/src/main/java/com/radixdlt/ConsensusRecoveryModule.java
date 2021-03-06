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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.store.LastEpochProof;
import java.util.Optional;

/**
 * Manages consensus recovery on restarts
 */
public class ConsensusRecoveryModule extends AbstractModule {
	@Provides
	private ViewUpdate view(
		VerifiedVertexStoreState vertexStoreState,
		ProposerElection proposerElection
	) {
		HighQC highQC = vertexStoreState.getHighQC();
		View view = highQC.highestQC().getView().next();
		final BFTNode leader = proposerElection.getProposer(view);
		final BFTNode nextLeader = proposerElection.getProposer(view.next());

		return ViewUpdate.create(view, highQC, leader, nextLeader);
	}

	@Provides
	@Singleton
	private BFTConfiguration initialConfig(
		BFTValidatorSet validatorSet,
		VerifiedVertexStoreState vertexStoreState
	) {
		return new BFTConfiguration(validatorSet, vertexStoreState);
	}

	@Provides
	private BFTValidatorSet validatorSet(
		@LastEpochProof VerifiedLedgerHeaderAndProof lastEpochProof
	) {
		return lastEpochProof.getNextValidatorSet().orElseThrow(() -> new IllegalStateException("Genesis has no validator set"));
	}

	@Provides
	@Singleton
	private SafetyState safetyState(EpochChange initialEpoch, PersistentSafetyStateStore safetyStore) {
		return safetyStore.get().flatMap(safetyState -> {
			final long safetyStateEpoch =
				safetyState.getLastVote().map(Vote::getEpoch).orElse(0L);

			if (safetyStateEpoch > initialEpoch.getEpoch()) {
				throw new IllegalStateException(
					String.format(
						"Last vote is in a future epoch. Vote epoch: %s, Epoch: %s",
						safetyStateEpoch,
						initialEpoch.getEpoch()
					)
				);
			} else if (safetyStateEpoch == initialEpoch.getEpoch()) {
				return Optional.of(safetyState);
			} else {
				return Optional.empty();
			}
		}).orElse(new SafetyState());
	}


}
