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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;

import java.util.Objects;

/**
 * A helper class to help in constructing a BFT validator state machine
 */
public final class BFTBuilder {
	// BFT Configuration objects
	private final BFTValidatorSet validatorSet;
	private final Hasher hasher;
	private final HashVerifier verifier;

	// BFT Stateful objects
	private final Pacemaker pacemaker;
	private final VertexStore vertexStore;
	private final BFTSyncer bftSyncer;
	private final EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher;
	private final EventDispatcher<NoVote> noVoteEventDispatcher;

	// Instance specific objects
	private final BFTNode self;

	private final ViewUpdate viewUpdate;
	private final RemoteEventDispatcher<Vote> voteDispatcher;
	private final SafetyRules safetyRules;

	public BFTBuilder(BFTNode self,
					  ViewUpdate viewUpdate,
					  RemoteEventDispatcher<Vote> voteDispatcher,
					  SafetyRules safetyRules,
					  Hasher hasher,
					  HashVerifier verifier,
					  BFTValidatorSet validatorSet,
					  Pacemaker pacemaker,
					  VertexStore vertexStore,
					  BFTSyncer syncer,
					  EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher,
					  EventDispatcher<NoVote> noVoteEventDispatcher) {

		this.self = Objects.requireNonNull(self);
		this.viewUpdate = Objects.requireNonNull(viewUpdate);
		this.voteDispatcher = Objects.requireNonNull(voteDispatcher);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.hasher = Objects.requireNonNull(hasher);
		this.verifier = Objects.requireNonNull(verifier);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.bftSyncer = Objects.requireNonNull(syncer);
		this.viewQuorumReachedEventDispatcher = Objects.requireNonNull(viewQuorumReachedEventDispatcher);
		this.noVoteEventDispatcher = Objects.requireNonNull(noVoteEventDispatcher);
	}

	public BFTEventProcessor build() {
		if (!validatorSet.containsNode(self)) {
			return EmptyBFTEventProcessor.INSTANCE;
		}
		final PendingVotes pendingVotes = new PendingVotes(hasher);

		BFTEventReducer reducer = new BFTEventReducer(
			self,
			pacemaker,
			vertexStore,
			viewQuorumReachedEventDispatcher,
			noVoteEventDispatcher,
			voteDispatcher,
			hasher,
			safetyRules,
			validatorSet,
			pendingVotes,
			viewUpdate
		);

		BFTEventPreprocessor preprocessor = new BFTEventPreprocessor(
			reducer,
			bftSyncer,
			viewUpdate
		);

		return new BFTEventVerifier(
			validatorSet,
			preprocessor,
			hasher,
			verifier
		);
	}
}
