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
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.environment.EventDispatcher;

/**
 * A helper class to help in constructing a BFT validator state machine
 */
public final class BFTBuilder {
	// BFT Configuration objects
	private BFTValidatorSet validatorSet;
	private ProposerElection proposerElection;
	private Hasher hasher;
	private HashVerifier verifier;

	// BFT Stateful objects
	private Pacemaker pacemaker;
	private VertexStore vertexStore;
	private BFTSyncer bftSyncer;
	private EventDispatcher<FormedQC> formedQCEventDispatcher;

	// Instance specific objects
	private BFTNode self;

	private BFTBuilder() {
		// Just making this inaccessible
	}

	public static BFTBuilder create() {
		return new BFTBuilder();
	}

	public BFTBuilder self(BFTNode self) {
		this.self = self;
		return this;
	}

	public BFTBuilder hasher(Hasher hasher) {
		this.hasher = hasher;
		return this;
	}

	public BFTBuilder verifier(HashVerifier verifier) {
		this.verifier = verifier;
		return this;
	}

	public BFTBuilder validatorSet(BFTValidatorSet validatorSet) {
		this.validatorSet = validatorSet;
		return this;
	}

	public BFTBuilder pacemaker(Pacemaker pacemaker) {
		this.pacemaker = pacemaker;
		return this;
	}

	public BFTBuilder vertexStore(VertexStore vertexStore) {
		this.vertexStore = vertexStore;
		return this;
	}

	public BFTBuilder bftSyncer(BFTSyncer bftSyncer) {
		this.bftSyncer = bftSyncer;
		return this;
	}

	public BFTBuilder formedQCEventDispatcher(EventDispatcher<FormedQC> formedQCEventDispatcher) {
		this.formedQCEventDispatcher = formedQCEventDispatcher;
		return this;
	}

	public BFTBuilder proposerElection(ProposerElection proposerElection) {
		this.proposerElection = proposerElection;
		return this;
	}

	public BFTEventProcessor build() {
		if (!validatorSet.containsNode(self)) {
			return EmptyBFTEventProcessor.INSTANCE;
		}

		BFTEventReducer reducer = new BFTEventReducer(
			pacemaker,
			vertexStore,
			formedQCEventDispatcher
		);

		SyncQueues syncQueues = new SyncQueues();

		BFTEventPreprocessor preprocessor = new BFTEventPreprocessor(
			self,
			reducer,
			pacemaker,
			bftSyncer,
			proposerElection,
			syncQueues
		);

		return new BFTEventVerifier(
			validatorSet,
			preprocessor,
			hasher,
			verifier
		);
	}
}
