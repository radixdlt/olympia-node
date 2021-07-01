/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkManager;
import java.util.Objects;

@Singleton
public class ForkVoteStatusService {

	public enum ForkVoteStatus {
		VOTE_REQUIRED, NO_ACTION_NEEDED
	}

	private final BFTNode self;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final ForkManager forkManager;

	@Inject
	public ForkVoteStatusService(
		@Self BFTNode self,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		ForkManager forkManager
	) {
		this.self = Objects.requireNonNull(self);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.forkManager = Objects.requireNonNull(forkManager);
	}

	public ForkVoteStatus forkVoteStatus() {
		if (forkManager.forkConfigs().size() == 1) {
			// no known forks other than the "genesis" fork
			return ForkVoteStatus.NO_ACTION_NEEDED;
		}

		final var stakedValidators = radixEngine.getComputedState(StakedValidators.class);

		final var expectedCandidateForkVoteHash =
			forkManager.getCandidateFork()
				.map(f -> ForkConfig.voteHash(self.getKey(), f));

		final var hasVotedIfNeeded =
			expectedCandidateForkVoteHash.isEmpty() || // all good if there's no candidate fork
				(stakedValidators.getForksVotes().containsKey(self.getKey()) // else existing vote hash must be present and match
					&& stakedValidators.getForksVotes().get(self.getKey()).equals(expectedCandidateForkVoteHash.get())
				);

		return hasVotedIfNeeded ? ForkVoteStatus.NO_ACTION_NEEDED : ForkVoteStatus.VOTE_REQUIRED;
	}
}
