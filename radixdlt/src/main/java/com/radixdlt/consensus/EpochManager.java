/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.consensus;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker.TimeoutSender;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.middleware2.CommittedAtom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages Epochs and the BFT instance (which is mostly epoch agnostic) associated with each epoch
 */
@NotThreadSafe
public class EpochManager {
	private static final Logger log = LogManager.getLogger("EM");
	private static final BFTEventProcessor EMPTY_PROCESSOR = new EmptyBFTEventProcessor();

	private final SyncEpochsRPCSender epochsRPCSender;
	private final PacemakerFactory pacemakerFactory;
	private final VertexStoreFactory vertexStoreFactory;
	private final ProposerElectionFactory proposerElectionFactory;
	private final ECPublicKey selfPublicKey;
	private final SystemCounters counters;
	private final ScheduledTimeoutSender scheduledTimeoutSender;
	private final SyncedStateComputer<CommittedAtom> syncedStateComputer;
	private final Map<Long, List<ConsensusEvent>> queuedEvents;
	private final BFTFactory bftFactory;

	private VertexMetadata currentAncestor;
	private VertexStore vertexStore;
	private BFTEventProcessor eventProcessor = EMPTY_PROCESSOR;
	private int numQueuedConsensusEvents = 0;

	public EpochManager(
		SyncedStateComputer<CommittedAtom> syncedStateComputer,
		SyncEpochsRPCSender epochsRPCSender,
		ScheduledTimeoutSender scheduledTimeoutSender,
		PacemakerFactory pacemakerFactory,
		VertexStoreFactory vertexStoreFactory,
		ProposerElectionFactory proposerElectionFactory,
		BFTFactory bftFactory,
		ECPublicKey selfPublicKey,
		SystemCounters counters
	) {
		this.syncedStateComputer = Objects.requireNonNull(syncedStateComputer);
		this.epochsRPCSender = Objects.requireNonNull(epochsRPCSender);
		this.scheduledTimeoutSender = Objects.requireNonNull(scheduledTimeoutSender);
		this.pacemakerFactory = Objects.requireNonNull(pacemakerFactory);
		this.vertexStoreFactory = Objects.requireNonNull(vertexStoreFactory);
		this.proposerElectionFactory = Objects.requireNonNull(proposerElectionFactory);
		this.bftFactory = bftFactory;
		this.selfPublicKey = Objects.requireNonNull(selfPublicKey);
		this.counters = Objects.requireNonNull(counters);
		this.queuedEvents = new HashMap<>();
	}

	private long currentEpoch() {
		return (this.currentAncestor == null) ? 0 : this.currentAncestor.getEpoch() + 1;
	}

	public void processEpochChange(EpochChange epochChange) {
		ValidatorSet validatorSet = epochChange.getValidatorSet();
		log.info("NEXT_EPOCH: {} {}", epochChange);

		VertexMetadata ancestorMetadata = epochChange.getAncestor();
		Vertex genesisVertex = Vertex.createGenesis(ancestorMetadata);
		final long nextEpoch = genesisVertex.getEpoch();

		// Sanity check
		if (nextEpoch <= this.currentEpoch()) {
			throw new IllegalStateException("Epoch change has already occurred: " + epochChange);
		}

		this.currentAncestor = ancestorMetadata;
		this.counters.set(CounterType.EPOCH_MANAGER_EPOCH, nextEpoch);

		if (!validatorSet.containsKey(selfPublicKey)) {
			log.info("NEXT_EPOCH: Not a validator");
			this.eventProcessor = EMPTY_PROCESSOR;
			this.vertexStore = null;
		} else {
			ProposerElection proposerElection = proposerElectionFactory.create(validatorSet);
			TimeoutSender sender = (view, ms) -> scheduledTimeoutSender.scheduleTimeout(new LocalTimeout(nextEpoch, view), ms);
			Pacemaker pacemaker = pacemakerFactory.create(sender);

			QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(genesisVertex);

			this.vertexStore = vertexStoreFactory.create(genesisVertex, genesisQC, syncedStateComputer);

			BFTEventProcessor reducer = bftFactory.create(
				pacemaker,
				this.vertexStore,
				proposerElection,
				validatorSet
			);

			SyncQueues syncQueues = new SyncQueues(
				validatorSet.getValidators().stream()
					.map(Validator::nodeKey)
					.collect(ImmutableSet.toImmutableSet()),
				this.counters
			);

			this.eventProcessor = new BFTEventPreprocessor(
				this.selfPublicKey,
				reducer,
				pacemaker,
				this.vertexStore,
				proposerElection,
				syncQueues
			);
		}

		this.eventProcessor.start();

		// Execute any queued up consensus events
		final List<ConsensusEvent> queuedEventsForEpoch = queuedEvents.getOrDefault(nextEpoch, Collections.emptyList());
		for (ConsensusEvent consensusEvent : queuedEventsForEpoch) {
			this.processConsensusEventInternal(consensusEvent);
		}

		numQueuedConsensusEvents -= queuedEventsForEpoch.size();
		counters.set(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS, numQueuedConsensusEvents);
		queuedEvents.remove(nextEpoch);
	}

	public void processGetVerticesRequest(GetVerticesRequest request) {
		if (this.vertexStore == null) {
			return;
		}

		vertexStore.processGetVerticesRequest(request);
	}

	public void processGetVerticesErrorResponse(GetVerticesErrorResponse response) {
		if (this.vertexStore == null) {
			return;
		}

		vertexStore.processGetVerticesErrorResponse(response);
	}

	public void processGetVerticesResponse(GetVerticesResponse response) {
		if (this.vertexStore == null) {
			return;
		}

		vertexStore.processGetVerticesResponse(response);
	}

	public void processGetEpochRequest(GetEpochRequest request) {
		if (this.currentEpoch() == request.getEpoch()) {
			epochsRPCSender.sendGetEpochResponse(request.getSender(), this.currentAncestor);
		} else {
			// TODO: Send better error message back
			epochsRPCSender.sendGetEpochResponse(request.getSender(), null);
		}
	}

	public void processGetEpochResponse(GetEpochResponse response) {
		if (response.getEpochAncestor() == null) {
			log.warn("Received empty GetEpochResponse {}", response);
			return;
		}

		final VertexMetadata ancestor = response.getEpochAncestor();
		if (ancestor.getEpoch() + 1 > this.currentEpoch()) {
			syncedStateComputer.syncTo(ancestor, Collections.singletonList(response.getSender()), null);
		}
	}

	private void processConsensusEventInternal(ConsensusEvent consensusEvent) {
		if (consensusEvent instanceof NewView) {
			eventProcessor.processNewView((NewView) consensusEvent);
		} else if (consensusEvent instanceof Proposal) {
			eventProcessor.processProposal((Proposal) consensusEvent);
		} else if (consensusEvent instanceof Vote) {
			eventProcessor.processVote((Vote) consensusEvent);
		} else {
			throw new IllegalStateException("Unknown consensus event: " + consensusEvent);
		}
	}

	public void processConsensusEvent(ConsensusEvent consensusEvent) {
		// TODO: Add the rest of consensus event verification here including signature verification

		if (consensusEvent.getEpoch() > this.currentEpoch()) {
			log.warn("Received higher epoch event {} from current epoch: {}", consensusEvent, this.currentEpoch());

			// queue higher epoch events for later processing
			// TODO: need to clear this by some rule (e.g. timeout or max size) or else memory leak attack possible
			queuedEvents.computeIfAbsent(consensusEvent.getEpoch(), e -> new ArrayList<>()).add(consensusEvent);
			numQueuedConsensusEvents++;
			counters.set(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS, numQueuedConsensusEvents);

			// Send request for higher epoch proof
			epochsRPCSender.sendGetEpochRequest(consensusEvent.getAuthor(), this.currentEpoch() + 1);
			return;
		}

		if (consensusEvent.getEpoch() < this.currentEpoch()) {
			log.warn("Received lower epoch event {} from current epoch: {}", consensusEvent, this.currentEpoch());
			return;
		}

		this.processConsensusEventInternal(consensusEvent);
	}

	public void processLocalTimeout(LocalTimeout localTimeout) {
		if (localTimeout.getEpoch() != this.currentEpoch()) {
			return;
		}

		eventProcessor.processLocalTimeout(localTimeout.getView());
	}

	public void processLocalSync(Hash synced) {
		eventProcessor.processLocalSync(synced);
	}

	public void processCommittedStateSync(CommittedStateSync committedStateSync) {
		if (vertexStore == null) {
			return;
		}

		vertexStore.processCommittedStateSync(committedStateSync);
	}
}
