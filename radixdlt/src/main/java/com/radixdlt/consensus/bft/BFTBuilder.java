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
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTInfoSender;
import com.radixdlt.consensus.bft.BFTEventReducer.EndOfEpochSender;
import com.radixdlt.consensus.liveness.MempoolProposalGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.TimeSupplier;

/**
 * A helper class to help in constructing a BFT validator state machine
 */
public final class BFTBuilder {
	// Connected modules
	private Mempool mempool;
	private BFTEventSender eventSender;
	private EndOfEpochSender endOfEpochSender;
	private SystemCounters counters;
	private TimeSupplier timeSupplier;

	// BFT Configuration objects
	private BFTValidatorSet validatorSet;
	private ProposerElection proposerElection;
	private boolean verifyAuthors = true;
	private Hasher hasher = new DefaultHasher();
	private HashSigner signer;
	private HashVerifier verifier = ECPublicKey::verify;
	private BFTInfoSender infoSender;

	// BFT Stateful objects
	private Pacemaker pacemaker;
	private VertexStore vertexStore;

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

	public BFTBuilder mempool(Mempool mempool) {
		this.mempool = mempool;
		return this;
	}

	public BFTBuilder eventSender(BFTEventSender eventSender) {
		this.eventSender = eventSender;
		return this;
	}

	public BFTBuilder verifyAuthors(boolean verifyAuthors) {
		this.verifyAuthors = verifyAuthors;
		return this;
	}

	public BFTBuilder hasher(Hasher hasher) {
		this.hasher = hasher;
		return this;
	}

	public BFTBuilder signer(HashSigner signer) {
		this.signer = signer;
		return this;
	}

	public BFTBuilder verifier(HashVerifier verifier) {
		this.verifier = verifier;
		return this;
	}

	public BFTBuilder endOfEpochSender(EndOfEpochSender endOfEpochSender) {
		this.endOfEpochSender = endOfEpochSender;
		return this;
	}

	public BFTBuilder validatorSet(BFTValidatorSet validatorSet) {
		this.validatorSet = validatorSet;
		return this;
	}

	public BFTBuilder counters(SystemCounters counters) {
		this.counters = counters;
		return this;
	}

	public BFTBuilder infoSender(BFTInfoSender infoSender) {
		this.infoSender = infoSender;
		return this;
	}

	public BFTBuilder timeSupplier(TimeSupplier timeSupplier) {
		this.timeSupplier = timeSupplier;
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

	public BFTBuilder proposerElection(ProposerElection proposerElection) {
		this.proposerElection = proposerElection;
		return this;
	}

	public BFTEventProcessor build() {
		final ProposalGenerator proposalGenerator = new MempoolProposalGenerator(vertexStore, mempool);
		final SafetyRules safetyRules = new SafetyRules(self, SafetyState.initialState(), hasher, countingSigner(counters, signer));
		// PendingVotes needs a hasher that produces unique values, as it indexes by hash
		final PendingVotes pendingVotes = new PendingVotes(new DefaultHasher());

		BFTEventReducer reducer = new BFTEventReducer(
			self,
			proposalGenerator,
			eventSender,
			endOfEpochSender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			validatorSet,
			counters,
			infoSender,
			timeSupplier
		);

		SyncQueues syncQueues = new SyncQueues();

		BFTEventPreprocessor preprocessor = new BFTEventPreprocessor(
			self,
			reducer,
			pacemaker,
			vertexStore,
			proposerElection,
			syncQueues
		);

		if (!verifyAuthors) {
			return preprocessor;
		}

		return new BFTEventVerifier(
			self,
			validatorSet,
			preprocessor,
			hasher,
			countingVerifier(counters, verifier)
		);
	}

	private static HashVerifier countingVerifier(SystemCounters counters, HashVerifier verifier) {
		return (pubKey, hash, sig) -> {
			counters.increment(SystemCounters.CounterType.SIGNATURES_VERIFIED);
			return verifier.verify(pubKey, hash, sig);
		};
	}

	private static HashSigner countingSigner(SystemCounters counters, HashSigner signer) {
		return hash -> {
			counters.increment(SystemCounters.CounterType.SIGNATURES_SIGNED);
			return signer.sign(hash);
		};
	}
}
