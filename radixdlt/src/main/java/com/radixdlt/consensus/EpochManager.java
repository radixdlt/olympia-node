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
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
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
public final class EpochManager {
	private static final Logger log = LogManager.getLogger("EM");

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
	private final String loggerPrefix;

	private VertexMetadata lastConstructed = null;
	private VertexMetadata currentAncestor;
	private VertexStoreEventProcessor vertexStoreEventProcessor = EmptyVertexStoreEventProcessor.INSTANCE;
	private BFTEventProcessor bftEventProcessor = EmptyBFTEventProcessor.INSTANCE;
	private int numQueuedConsensusEvents = 0;

	public EpochManager(
		String loggerPrefix,
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
		this.loggerPrefix = Objects.requireNonNull(loggerPrefix);
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
		VertexMetadata ancestorMetadata = epochChange.getAncestor();
		Vertex genesisVertex = Vertex.createGenesis(ancestorMetadata);
		final long nextEpoch = genesisVertex.getEpoch();

		// Sanity check
		if (nextEpoch <= this.currentEpoch()) {
			throw new IllegalStateException("Epoch change has already occurred: " + epochChange);
		}

		// If constructed the end of the previous epoch then broadcast new epoch to new validator set
		if (Objects.equals(lastConstructed, ancestorMetadata)) {
			log.info("{}: EPOCH_CHANGE: broadcasting next epoch", this.loggerPrefix);
			for (Validator validator : validatorSet.getValidators()) {
				if (!validator.nodeKey().equals(selfPublicKey)) {
					epochsRPCSender.sendGetEpochResponse(validator.nodeKey(), ancestorMetadata);
				}
			}
		}

		this.currentAncestor = ancestorMetadata;
		this.counters.set(CounterType.EPOCH_MANAGER_EPOCH, nextEpoch);

		final BFTEventProcessor bftEventProcessor;
		final VertexStoreEventProcessor vertexStoreEventProcessor;

		if (!validatorSet.containsKey(selfPublicKey)) {
			log.info("{}: EPOCH_CHANGE: {} Not part of validator set", this.loggerPrefix, epochChange);
			bftEventProcessor =  EmptyBFTEventProcessor.INSTANCE;
			vertexStoreEventProcessor = EmptyVertexStoreEventProcessor.INSTANCE;
		} else {
			log.info("{}: EPOCH_CHANGE: {} Part of validator set", this.loggerPrefix, epochChange);
			ProposerElection proposerElection = proposerElectionFactory.create(validatorSet);
			TimeoutSender sender = (view, ms) -> scheduledTimeoutSender.scheduleTimeout(new LocalTimeout(nextEpoch, view), ms);
			Pacemaker pacemaker = pacemakerFactory.create(sender);
			QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(genesisVertex);
			VertexStore vertexStore = vertexStoreFactory.create(genesisVertex, genesisQC, syncedStateComputer);
			BFTEventProcessor reducer = bftFactory.create(
				this::processEndOfEpoch,
				pacemaker,
				vertexStore,
				proposerElection,
				validatorSet
			);
			SyncQueues syncQueues = new SyncQueues(
				validatorSet.getValidators().stream()
					.map(Validator::nodeKey)
					.collect(ImmutableSet.toImmutableSet()),
				this.counters
			);

			vertexStoreEventProcessor = vertexStore;
			bftEventProcessor = new BFTEventPreprocessor(
				this.selfPublicKey,
				reducer,
				pacemaker,
				vertexStore,
				proposerElection,
				syncQueues
			);
		}

		// Update processors
		this.bftEventProcessor = bftEventProcessor;
		this.vertexStoreEventProcessor = vertexStoreEventProcessor;

		this.bftEventProcessor.start();

		// Execute any queued up consensus events
		final List<ConsensusEvent> queuedEventsForEpoch = queuedEvents.getOrDefault(nextEpoch, Collections.emptyList());
		for (ConsensusEvent consensusEvent : queuedEventsForEpoch) {
			this.processConsensusEventInternal(consensusEvent);
		}

		numQueuedConsensusEvents -= queuedEventsForEpoch.size();
		counters.set(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS, numQueuedConsensusEvents);
		queuedEvents.remove(nextEpoch);
	}

	private void processEndOfEpoch(VertexMetadata vertexMetadata) {
		log.info("{}: END_OF_EPOCH: {}", this.loggerPrefix, vertexMetadata);
		if (this.lastConstructed == null || this.lastConstructed.getEpoch() < vertexMetadata.getEpoch()) {
			this.lastConstructed = vertexMetadata;

			// Stop processing new events if end of epoch
			// but keep VertexStore alive to help others in vertex syncing
			this.bftEventProcessor = EmptyBFTEventProcessor.INSTANCE;
		}
	}

	public void processGetEpochRequest(GetEpochRequest request) {
		log.info("{}: GET_EPOCH_REQUEST: {}", this.loggerPrefix, request);

		if (this.currentEpoch() == request.getEpoch()) {
			epochsRPCSender.sendGetEpochResponse(request.getAuthor(), this.currentAncestor);
		} else {
			// TODO: Send better error message back
			epochsRPCSender.sendGetEpochResponse(request.getAuthor(), null);
		}
	}

	public void processGetEpochResponse(GetEpochResponse response) {
		log.info("{}: GET_EPOCH_RESPONSE: {}", this.loggerPrefix, response);

		if (response.getEpochAncestor() == null) {
			log.warn("{}: Received empty GetEpochResponse {}", this.loggerPrefix, response);
			// TODO: retry
			return;
		}

		final VertexMetadata ancestor = response.getEpochAncestor();
		if (ancestor.getEpoch() >= this.currentEpoch()) {
			syncedStateComputer.syncTo(ancestor, Collections.singletonList(response.getAuthor()), null);
		} else {
			log.warn("{}: Received old epoch {}", this.loggerPrefix, response);
		}
	}

	private void processConsensusEventInternal(ConsensusEvent consensusEvent) {
		if (consensusEvent instanceof NewView) {
			bftEventProcessor.processNewView((NewView) consensusEvent);
		} else if (consensusEvent instanceof Proposal) {
			bftEventProcessor.processProposal((Proposal) consensusEvent);
		} else if (consensusEvent instanceof Vote) {
			bftEventProcessor.processVote((Vote) consensusEvent);
		} else {
			throw new IllegalStateException("Unknown consensus event: " + consensusEvent);
		}
	}

	public void processConsensusEvent(ConsensusEvent consensusEvent) {
		// TODO: Add the rest of consensus event verification here including signature verification

		if (consensusEvent.getEpoch() > this.currentEpoch()) {
			log.warn("{}: CONSENSUS_EVENT: Received higher epoch event: {} current epoch: {}",
				this.loggerPrefix, consensusEvent, this.currentEpoch()
			);

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
			log.warn("{}: CONSENSUS_EVENT: Received lower epoch event: {} current epoch: {}",
				this.loggerPrefix, consensusEvent, this.currentEpoch()
			);
			return;
		}

		this.processConsensusEventInternal(consensusEvent);
	}

	public void processLocalTimeout(LocalTimeout localTimeout) {
		if (localTimeout.getEpoch() != this.currentEpoch()) {
			return;
		}

		bftEventProcessor.processLocalTimeout(localTimeout.getView());
	}

	public void processLocalSync(Hash synced) {
		bftEventProcessor.processLocalSync(synced);
	}

	public void processGetVerticesRequest(GetVerticesRequest request) {
		vertexStoreEventProcessor.processGetVerticesRequest(request);
	}

	public void processGetVerticesErrorResponse(GetVerticesErrorResponse response) {
		vertexStoreEventProcessor.processGetVerticesErrorResponse(response);
	}

	public void processGetVerticesResponse(GetVerticesResponse response) {
		vertexStoreEventProcessor.processGetVerticesResponse(response);
	}

	public void processCommittedStateSync(CommittedStateSync committedStateSync) {
		vertexStoreEventProcessor.processCommittedStateSync(committedStateSync);
	}
}
