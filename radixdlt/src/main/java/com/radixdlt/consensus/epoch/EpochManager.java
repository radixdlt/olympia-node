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

package com.radixdlt.consensus.epoch;

import com.google.common.collect.ImmutableList;
import com.google.inject.name.Named;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.EmptyVertexStoreEventProcessor;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VertexStoreEventProcessor;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.EmptyBFTEventProcessor;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker.TimeoutSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages Epochs and the BFT instance (which is mostly epoch agnostic) associated with each epoch
 */
@NotThreadSafe
public final class EpochManager {
	private static final Logger log = LogManager.getLogger();

	/**
	 * A sender of GetEpoch RPC requests/responses
	 */
	public interface SyncEpochsRPCSender {

		/**
		 * Send a request to a peer for proof of an epoch
		 * @param node the peer to send to
		 * @param epoch the epoch to retrieve proof for
		 */
		void sendGetEpochRequest(BFTNode node, long epoch);

		/**
		 * Send an epoch proof resposne to a peer
		 *
		 * TODO: currently just actually sending an ancestor but should contain
		 * TODO: proof as well
		 *
		 * @param node the peer to send to
		 * @param ancestor the ancestor of the epoch
		 */
		void sendGetEpochResponse(BFTNode node, VertexMetadata ancestor);
	}

	public interface EpochInfoSender {
		/**
		 * Signify that the bft node is on a new view
		 * @param epochView the epoch and view the bft node has changed to
		 */
		void sendCurrentView(EpochView epochView);

		/**
		 * Signify that a timeout was processed by this bft node
		 * @param timeout the timeout
		 */
		void sendTimeoutProcessed(Timeout timeout);
	}

	private final BFTNode self;
	private final SyncEpochsRPCSender epochsRPCSender;
	private final PacemakerFactory pacemakerFactory;
	private final VertexStoreFactory vertexStoreFactory;
	private final ProposerElectionFactory proposerElectionFactory;
	private final SystemCounters counters;
	private final LocalTimeoutSender localTimeoutSender;
	private final Ledger ledger;
	private final Map<Long, List<ConsensusEvent>> queuedEvents;
	private final BFTFactory bftFactory;
	private final EpochInfoSender epochInfoSender;
	private final SyncRequestSender syncRequestSender;

	private VertexMetadata lastConstructed = null;
	private EpochChange currentEpoch;
	private VertexStoreEventProcessor vertexStoreEventProcessor;
	private BFTEventProcessor bftEventProcessor;
	private int numQueuedConsensusEvents = 0;

	@Inject
	public EpochManager(
		@Named("self") BFTNode self,
		EpochChange initialEpoch,
		Ledger ledger,
		SyncEpochsRPCSender epochsRPCSender,
		LocalTimeoutSender localTimeoutSender,
		SyncRequestSender syncRequestSender,
		PacemakerFactory pacemakerFactory,
		VertexStoreFactory vertexStoreFactory,
		ProposerElectionFactory proposerElectionFactory,
		BFTFactory bftFactory,
		SystemCounters counters,
		EpochInfoSender epochInfoSender
	) {
		this.currentEpoch = Objects.requireNonNull(initialEpoch);
		this.self = Objects.requireNonNull(self);
		this.ledger = Objects.requireNonNull(ledger);
		this.epochsRPCSender = Objects.requireNonNull(epochsRPCSender);
		this.syncRequestSender = Objects.requireNonNull(syncRequestSender);
		this.localTimeoutSender = Objects.requireNonNull(localTimeoutSender);
		this.pacemakerFactory = Objects.requireNonNull(pacemakerFactory);
		this.vertexStoreFactory = Objects.requireNonNull(vertexStoreFactory);
		this.proposerElectionFactory = Objects.requireNonNull(proposerElectionFactory);
		this.bftFactory = bftFactory;
		this.counters = Objects.requireNonNull(counters);
		this.epochInfoSender = Objects.requireNonNull(epochInfoSender);
		this.queuedEvents = new HashMap<>();
	}

	private void updateEpochState() {
		BFTValidatorSet validatorSet = this.currentEpoch.getValidatorSet();
		VertexMetadata ancestorMetadata = this.currentEpoch.getAncestor();

		final BFTEventProcessor bftEventProcessor;
		final VertexStoreEventProcessor vertexStoreEventProcessor;

		if (!validatorSet.containsNode(self)) {
			logEpochChange(this.currentEpoch, "excluded from");
			bftEventProcessor =  EmptyBFTEventProcessor.INSTANCE;
			vertexStoreEventProcessor = EmptyVertexStoreEventProcessor.INSTANCE;
		} else {
			logEpochChange(this.currentEpoch, "included in");

			Vertex genesisVertex = Vertex.createGenesis(ancestorMetadata);
			final long nextEpoch = genesisVertex.getEpoch();

			ProposerElection proposerElection = proposerElectionFactory.create(validatorSet);
			TimeoutSender sender = (view, ms) -> localTimeoutSender.scheduleTimeout(new LocalTimeout(nextEpoch, view), ms);
			Pacemaker pacemaker = pacemakerFactory.create(sender);

			// TODO: Recover VertexStore
			QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(genesisVertex);
			VertexStore vertexStore = vertexStoreFactory.create(genesisVertex, genesisQC, ledger);
			vertexStoreEventProcessor = vertexStore;

			BFTInfoSender infoSender = new BFTInfoSender() {
				@Override
				public void sendCurrentView(View view) {
					epochInfoSender.sendCurrentView(EpochView.of(nextEpoch, view));
				}

				@Override
				public void sendTimeoutProcessed(View view, BFTNode leader) {
					Timeout timeout = new Timeout(EpochView.of(nextEpoch, view), leader);
					epochInfoSender.sendTimeoutProcessed(timeout);
				}
			};

			bftEventProcessor = bftFactory.create(
				this::processEndOfEpoch,
				pacemaker,
				vertexStore,
				proposerElection,
				validatorSet,
				infoSender
			);
		}

		// Update processors
		this.bftEventProcessor = bftEventProcessor;
		this.vertexStoreEventProcessor = vertexStoreEventProcessor;
	}

	public void start() {
		this.updateEpochState();
		this.bftEventProcessor.start();
	}

	private long currentEpoch() {
		return this.currentEpoch.getAncestor().getEpoch() + 1;
	}

	public void processEpochChange(EpochChange epochChange) {
		log.trace("{}: EPOCH_CHANGE: {}", this.self, epochChange);
		BFTValidatorSet validatorSet = epochChange.getValidatorSet();
		VertexMetadata ancestorMetadata = epochChange.getAncestor();
		Vertex genesisVertex = Vertex.createGenesis(ancestorMetadata);
		final long nextEpoch = genesisVertex.getEpoch();

		// Sanity check
		if (nextEpoch <= this.currentEpoch()) {
			throw new IllegalStateException("Epoch change has already occurred: " + epochChange);
		}

		// If constructed the end of the previous epoch then broadcast new epoch to new validator set
		// TODO: Move this into when lastConstructed is set
		if (lastConstructed != null && lastConstructed.getEpoch() == ancestorMetadata.getEpoch()) {
			log.info("{}: EPOCH_CHANGE: broadcasting next epoch", this.self);
			for (BFTValidator validator : validatorSet.getValidators()) {
				if (!validator.getNode().equals(self)) {
					epochsRPCSender.sendGetEpochResponse(validator.getNode(), ancestorMetadata);
				}
			}
		}

		this.currentEpoch = epochChange;
		this.updateEpochState();
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

	private void logEpochChange(EpochChange epochChange, String message) {
		if (log.isInfoEnabled()) {
			// Reduce complexity of epoch change log message, and make it easier to correlate with
			// other logs.  Size reduced from circa 6Kib to approx 1Kib over ValidatorSet.toString().
			StringBuilder epochMessage = new StringBuilder(this.self.getSimpleName());
			epochMessage.append(": EPOCH_CHANGE: ");
			epochMessage.append(message);
			epochMessage.append(" new epoch ").append(epochChange.getAncestor().getEpoch() + 1);
			epochMessage.append(" with ").append(epochChange.getValidatorSet().getValidators().size()).append(" validators: ");
			Iterator<BFTValidator> i = epochChange.getValidatorSet().getValidators().iterator();
			if (i.hasNext()) {
				appendValidator(epochMessage, i.next());
				while (i.hasNext()) {
					epochMessage.append(',');
					appendValidator(epochMessage, i.next());
				}
			} else {
				epochMessage.append("[NONE]");
			}
			log.info("{}", epochMessage);
		}
	}

	private void appendValidator(StringBuilder msg, BFTValidator v) {
		msg.append(v.getNode().getSimpleName()).append(':').append(v.getPower());
	}

	private void processEndOfEpoch(VertexMetadata vertexMetadata) {
		log.trace("{}: END_OF_EPOCH: {}", this.self, vertexMetadata);
		if (this.lastConstructed == null || this.lastConstructed.getEpoch() < vertexMetadata.getEpoch()) {
			this.lastConstructed = vertexMetadata;

			// Stop processing new events if end of epoch
			// but keep VertexStore alive to help others in vertex syncing
			this.bftEventProcessor = EmptyBFTEventProcessor.INSTANCE;
		}
	}

	public void processGetEpochRequest(GetEpochRequest request) {
		log.trace("{}: GET_EPOCH_REQUEST: {}", this.self, request);

		if (this.currentEpoch() > request.getEpoch()) {
			epochsRPCSender.sendGetEpochResponse(request.getAuthor(), this.currentEpoch.getAncestor());
		} else {
			log.warn("{}: GET_EPOCH_REQUEST: {} but currently on epoch: {}",
				this.self::getSimpleName, () -> request, this::currentEpoch
			);

			// TODO: Send better error message back
			epochsRPCSender.sendGetEpochResponse(request.getAuthor(), null);
		}
	}

	public void processGetEpochResponse(GetEpochResponse response) {
		log.trace("{}: GET_EPOCH_RESPONSE: {}", this.self, response);

		if (response.getEpochAncestor() == null) {
			log.warn("{}: Received empty GetEpochResponse {}", this.self, response);
			// TODO: retry
			return;
		}

		final VertexMetadata ancestor = response.getEpochAncestor();
		if (ancestor.getEpoch() >= this.currentEpoch()) {
			syncRequestSender.sendLocalSyncRequest(new LocalSyncRequest(ancestor, ImmutableList.of(response.getAuthor())));
		} else {
			log.info("{}: Ignoring old epoch {}", this.self, response);
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
		if (consensusEvent.getEpoch() > this.currentEpoch()) {
			log.debug("{}: CONSENSUS_EVENT: Received higher epoch event: {} current epoch: {}",
				this.self::getSimpleName, () -> consensusEvent, this::currentEpoch
			);

			// queue higher epoch events for later processing
			// TODO: need to clear this by some rule (e.g. timeout or max size) or else memory leak attack possible
			queuedEvents.computeIfAbsent(consensusEvent.getEpoch(), e -> new ArrayList<>()).add(consensusEvent);
			numQueuedConsensusEvents++;
			counters.set(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS, numQueuedConsensusEvents);

			// Send request for higher epoch proof
			epochsRPCSender.sendGetEpochRequest(consensusEvent.getAuthor(), this.currentEpoch());
			return;
		}

		if (consensusEvent.getEpoch() < this.currentEpoch()) {
			log.debug("{}: CONSENSUS_EVENT: Ignoring lower epoch event: {} current epoch: {}",
				this.self::getSimpleName, () -> consensusEvent, this::currentEpoch
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
