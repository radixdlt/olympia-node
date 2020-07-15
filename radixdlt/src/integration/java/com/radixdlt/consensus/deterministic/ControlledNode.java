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

package com.radixdlt.consensus.deterministic;

import com.radixdlt.consensus.bft.BFTEventReducer;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EmptySyncEpochsRPCSender;
import com.radixdlt.consensus.EpochChange;
import com.radixdlt.consensus.EpochManager;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.LocalTimeout;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ControlledSender;
import com.radixdlt.consensus.deterministic.configuration.UnsupportedSyncVerticesRPCSender;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.MempoolProposalGenerator;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.CommittedAtom;
import java.util.Objects;

/**
 * Controlled Node where its state machine is managed by a synchronous
 * processNext() call.
 */
class ControlledNode {
	private final EpochManager epochManager;
	private final SystemCounters systemCounters;
	private final ValidatorSet initialValidatorSet;
	private final ControlledSender controlledSender;

	public enum SyncAndTimeout {
		NONE,
		SYNC,
		SYNC_AND_TIMEOUT
	}

	ControlledNode(
		ECKeyPair key,
		ControlledSender sender,
		ProposerElectionFactory proposerElectionFactory,
		ValidatorSet initialValidatorSet,
		SyncAndTimeout syncAndTimeout,
		SyncedStateComputer<CommittedAtom> stateComputer
	) {
		this.systemCounters = new SystemCountersImpl();
		this.controlledSender = Objects.requireNonNull(sender);
		this.initialValidatorSet = Objects.requireNonNull(initialValidatorSet);

		SyncVerticesRPCSender syncVerticesRPCSender = (syncAndTimeout != SyncAndTimeout.NONE)
			? sender
			: UnsupportedSyncVerticesRPCSender.INSTANCE;
		LocalTimeoutSender localTimeoutSender = (syncAndTimeout == SyncAndTimeout.SYNC_AND_TIMEOUT) ? sender : (v, t) -> { };
		Mempool mempool = new EmptyMempool();
		Hasher nullHasher = data -> Hash.ZERO_HASH;
		Hasher defaultHasher = new DefaultHasher();
		HashSigner nullSigner = (k, h) -> new ECDSASignature();
		HashVerifier nullVerifier = (p, h, s) -> true;
		VertexStoreFactory vertexStoreFactory = (vertex, qc, syncedStateComputer) ->
			new VertexStore(vertex, qc, syncedStateComputer, syncVerticesRPCSender, sender, systemCounters);
		BFTNode self = new BFTNode(key.getPublicKey());
		BFTFactory bftFactory =
			(endOfEpochSender, pacemaker, vertexStore, proposerElection, validatorSet) -> {
				final ProposalGenerator proposalGenerator = new MempoolProposalGenerator(vertexStore, mempool);
				final SafetyRules safetyRules = new SafetyRules(key, SafetyState.initialState(), nullHasher, nullSigner);
				// PendingVotes needs a hasher that produces unique values, as it indexes by hash
				final PendingVotes pendingVotes = new PendingVotes(defaultHasher, nullVerifier);

				return new BFTEventReducer(
					self,
					proposalGenerator,
					controlledSender,
					endOfEpochSender,
					safetyRules,
					pacemaker,
					vertexStore,
					pendingVotes,
					proposerElection,
					validatorSet,
					systemCounters
				);
			};

		this.epochManager = new EpochManager(
			self,
			stateComputer,
			EmptySyncEpochsRPCSender.INSTANCE,
			localTimeoutSender,
			timeoutSender -> new FixedTimeoutPacemaker(1, timeoutSender, nullVerifier),
			vertexStoreFactory,
			proposerElectionFactory,
			bftFactory,
			systemCounters
		);
	}

	SystemCounters getSystemCounters() {
		return systemCounters;
	}

	void start() {
		EpochChange epochChange = new EpochChange(VertexMetadata.ofGenesisAncestor(), this.initialValidatorSet);
		controlledSender.epochChange(epochChange);
	}

	void processNext(Object msg) {
		if (msg instanceof EpochChange) {
			this.epochManager.processEpochChange((EpochChange) msg);
		} else if (msg instanceof GetVerticesRequest) {
			this.epochManager.processGetVerticesRequest((GetVerticesRequest) msg);
		} else if (msg instanceof GetVerticesResponse) {
			this.epochManager.processGetVerticesResponse((GetVerticesResponse) msg);
		} else if (msg instanceof GetVerticesErrorResponse) {
			this.epochManager.processGetVerticesErrorResponse((GetVerticesErrorResponse) msg);
		} else if (msg instanceof CommittedStateSync) {
			this.epochManager.processCommittedStateSync((CommittedStateSync) msg);
		} else if (msg instanceof LocalTimeout) {
			this.epochManager.processLocalTimeout((LocalTimeout) msg);
		} else if (msg instanceof ConsensusEvent) {
			this.epochManager.processConsensusEvent((ConsensusEvent) msg);
		} else if (msg instanceof Hash) {
			this.epochManager.processLocalSync((Hash) msg);
		} else {
			throw new IllegalStateException("Unknown msg: " + msg);
		}
	}
}
