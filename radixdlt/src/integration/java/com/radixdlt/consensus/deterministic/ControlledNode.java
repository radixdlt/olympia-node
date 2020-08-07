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

import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.epoch.EmptyEpochInfoSender;
import com.radixdlt.consensus.epoch.EmptySyncEpochsRPCSender;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ControlledSender;
import com.radixdlt.consensus.deterministic.configuration.UnsupportedSyncVerticesRPCSender;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.middleware2.CommittedAtom;
import java.util.Objects;

/**
 * Controlled Node where its state machine is managed by a synchronous
 * processNext() call.
 */
class ControlledNode {
	private final EpochManager epochManager;
	private final SystemCounters systemCounters;
	private final BFTValidatorSet initialValidatorSet;
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
		BFTValidatorSet initialValidatorSet,
		SyncAndTimeout syncAndTimeout,
		SyncedStateComputer<CommittedAtom> stateComputer
	) {
		this.systemCounters = new SystemCountersImpl();
		this.controlledSender = Objects.requireNonNull(sender);
		this.initialValidatorSet = Objects.requireNonNull(initialValidatorSet);

		NextCommandGenerator nextCommandGenerator = (view, aids) -> null;
		Hasher nullHasher = data -> Hash.ZERO_HASH;
		HashSigner nullSigner = h -> new ECDSASignature();
		BFTNode self = BFTNode.create(key.getPublicKey());
		BFTFactory bftFactory =
			(endOfEpochSender, pacemaker, vertexStore, proposerElection, validatorSet, bftInfoSender) ->
				BFTBuilder.create()
					.self(self)
					.endOfEpochSender(endOfEpochSender)
					.pacemaker(pacemaker)
					.nextCommandGenerator(nextCommandGenerator)
					.vertexStore(vertexStore)
					.proposerElection(proposerElection)
					.validatorSet(validatorSet)
					.eventSender(controlledSender)
					.counters(systemCounters)
					.infoSender(bftInfoSender)
					.timeSupplier(System::currentTimeMillis)
					.hasher(nullHasher)
					.signer(nullSigner)
					.verifier((k, hash, sig) -> true)
					.build();

		SyncVerticesRPCSender syncVerticesRPCSender = (syncAndTimeout != SyncAndTimeout.NONE)
			? sender
			: UnsupportedSyncVerticesRPCSender.INSTANCE;
		LocalTimeoutSender localTimeoutSender = (syncAndTimeout == SyncAndTimeout.SYNC_AND_TIMEOUT) ? sender : (v, t) -> { };
		VertexStoreFactory vertexStoreFactory = (vertex, qc, syncedStateComputer) ->
			new VertexStore(vertex, qc, syncedStateComputer, syncVerticesRPCSender, sender, sender, systemCounters);
		EpochInfoSender epochInfoSender = EmptyEpochInfoSender.INSTANCE;
		this.epochManager = new EpochManager(
			self,
			stateComputer,
			EmptySyncEpochsRPCSender.INSTANCE,
			localTimeoutSender,
			timeoutSender -> new FixedTimeoutPacemaker(1, timeoutSender),
			vertexStoreFactory,
			proposerElectionFactory,
			bftFactory,
			systemCounters,
			epochInfoSender
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
