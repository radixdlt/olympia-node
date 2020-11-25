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
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.PacemakerStateFactory;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.sync.EmptyBFTSyncResponseProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.BFTSyncResponseProcessor;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.BFTSyncRequestProcessor;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.EmptyBFTEventProcessor;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.LedgerUpdateProcessor;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages Epochs and the BFT instance (which is mostly epoch agnostic) associated with each epoch
 */
@NotThreadSafe
public final class EpochManager implements BFTSyncRequestProcessor {
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
		void sendGetEpochResponse(BFTNode node, VerifiedLedgerHeaderAndProof ancestor);
	}

	private static final Logger log = LogManager.getLogger();
	private final BFTNode self;
	private final SyncEpochsRPCSender epochsRPCSender;
	private final PacemakerFactory pacemakerFactory;
	private final VertexStoreFactory vertexStoreFactory;
	private final BFTSyncRequestProcessorFactory bftSyncRequestProcessorFactory;
	private final BFTSyncFactory bftSyncFactory;
	private final ProposerElectionFactory proposerElectionFactory;
	private final Hasher hasher;
	private final HashSigner signer;
	private final PacemakerTimeoutCalculator timeoutCalculator;
	private final SystemCounters counters;
	private final Map<Long, List<ConsensusEvent>> queuedEvents;
	private final BFTFactory bftFactory;
	private final EventDispatcher<LocalSyncRequest> localSyncRequestProcessor;
	private final PacemakerStateFactory pacemakerStateFactory;

	private EpochChange currentEpoch;
	private int numQueuedConsensusEvents = 0;

	private BFTSyncResponseProcessor syncBFTResponseProcessor;
	private EventProcessor<LocalGetVerticesRequest> syncTimeoutProcessor;
	private LedgerUpdateProcessor<LedgerUpdate> syncLedgerUpdateProcessor;
	private BFTSyncRequestProcessor syncRequestProcessor;
	private BFTEventProcessor bftEventProcessor;
	private Set<EventProcessor<BFTUpdate>> bftUpdateProcessors;

	private final PersistentSafetyStateStore persistentSafetyStateStore;

	@Inject
	public EpochManager(
		@Self BFTNode self,
		BFTEventProcessor initialBFTEventProcessor,
		BFTSyncRequestProcessor initialSyncRequestProcessor,
		BFTSync initialBFTSync,
		Set<EventProcessor<BFTUpdate>> initialBFTUpdateProcessors,
		EpochChange initialEpoch,
		SyncEpochsRPCSender epochsRPCSender,
		PacemakerFactory pacemakerFactory,
		VertexStoreFactory vertexStoreFactory,
		BFTSyncFactory bftSyncFactory,
		BFTSyncRequestProcessorFactory bftSyncRequestProcessorFactory,
		ProposerElectionFactory proposerElectionFactory,
		BFTFactory bftFactory,
		SystemCounters counters,
		EventDispatcher<LocalSyncRequest> localSyncRequestProcessor,
		Hasher hasher,
		HashSigner signer,
		PacemakerTimeoutCalculator timeoutCalculator,
		PacemakerStateFactory pacemakerStateFactory,
		PersistentSafetyStateStore persistentSafetyStateStore
	) {
		if (!initialEpoch.getBFTConfiguration().getValidatorSet().containsNode(self)) {
			this.bftEventProcessor =  EmptyBFTEventProcessor.INSTANCE;
			this.syncBFTResponseProcessor = EmptyBFTSyncResponseProcessor.INSTANCE;
			this.syncRequestProcessor = req -> { };
			this.syncLedgerUpdateProcessor = update -> { };
			this.syncTimeoutProcessor = timeout -> { };
		} else {
			this.bftEventProcessor = Objects.requireNonNull(initialBFTEventProcessor);
			this.syncBFTResponseProcessor = initialBFTSync;
			this.syncLedgerUpdateProcessor = initialBFTSync;
			this.syncTimeoutProcessor = initialBFTSync::processGetVerticesLocalTimeout;
			this.syncRequestProcessor = initialSyncRequestProcessor;
		}

		this.bftUpdateProcessors = initialBFTUpdateProcessors;
		this.currentEpoch = Objects.requireNonNull(initialEpoch);
		this.self = Objects.requireNonNull(self);
		this.epochsRPCSender = Objects.requireNonNull(epochsRPCSender);
		this.localSyncRequestProcessor = Objects.requireNonNull(localSyncRequestProcessor);
		this.pacemakerFactory = Objects.requireNonNull(pacemakerFactory);
		this.vertexStoreFactory = Objects.requireNonNull(vertexStoreFactory);
		this.bftSyncFactory = Objects.requireNonNull(bftSyncFactory);
		this.bftSyncRequestProcessorFactory = bftSyncRequestProcessorFactory;
		this.proposerElectionFactory = Objects.requireNonNull(proposerElectionFactory);
		this.hasher = Objects.requireNonNull(hasher);
		this.signer = Objects.requireNonNull(signer);
		this.timeoutCalculator = Objects.requireNonNull(timeoutCalculator);
		this.bftFactory = bftFactory;
		this.counters = Objects.requireNonNull(counters);
		this.pacemakerStateFactory = Objects.requireNonNull(pacemakerStateFactory);
		this.persistentSafetyStateStore = Objects.requireNonNull(persistentSafetyStateStore);
		this.queuedEvents = new HashMap<>();
	}

	private void updateEpochState() {
		BFTConfiguration config = this.currentEpoch.getBFTConfiguration();
		BFTValidatorSet validatorSet = config.getValidatorSet();
		if (!validatorSet.containsNode(self)) {
			logEpochChange(this.currentEpoch, "excluded from");
			this.bftEventProcessor =  EmptyBFTEventProcessor.INSTANCE;
			this.syncBFTResponseProcessor = EmptyBFTSyncResponseProcessor.INSTANCE;
			this.syncRequestProcessor = req -> { };
			this.syncLedgerUpdateProcessor = update -> { };
			this.syncTimeoutProcessor = timeout -> { };
			return;
		}

		final long nextEpoch = this.currentEpoch.getEpoch();
		logEpochChange(this.currentEpoch, "included in");

		// Config
		final BFTConfiguration bftConfiguration = this.currentEpoch.getBFTConfiguration();
		final ProposerElection proposerElection = proposerElectionFactory.create(validatorSet);
		final ViewUpdate initialViewUpdate = ViewUpdate.genesis();

		// Mutable Consensus State
		final VertexStore vertexStore = vertexStoreFactory.create(
			bftConfiguration.getGenesisVertex(),
			bftConfiguration.getGenesisQC()
		);
		final PacemakerState pacemakerState = pacemakerStateFactory.create(nextEpoch, proposerElection);

		// Consensus Drivers
		final SafetyRules safetyRules = new SafetyRules(self, SafetyState.initialState(), persistentSafetyStateStore, hasher, signer);
		final Pacemaker pacemaker = pacemakerFactory.create(
			validatorSet,
			vertexStore,
			pacemakerState,
			timeoutCalculator,
			safetyRules,
			initialViewUpdate,
			nextEpoch
		);
		final BFTSync bftSync = bftSyncFactory.create(vertexStore, pacemakerState);

		this.syncBFTResponseProcessor = bftSync;
		this.syncLedgerUpdateProcessor = bftSync;
		this.syncTimeoutProcessor = bftSync::processGetVerticesLocalTimeout;

		this.syncRequestProcessor = bftSyncRequestProcessorFactory.create(vertexStore);

		this.bftEventProcessor = bftFactory.create(
			self,
			pacemaker,
			vertexStore,
			bftSync,
			bftSync.formedQCEventProcessor(),
			validatorSet,
			counters,
			safetyRules
		);

		this.bftUpdateProcessors = ImmutableSet.of(bftSync::processBFTUpdate, bftEventProcessor::processBFTUpdate);
	}

	public void start() {
		log.info("EpochManager Start: {}", currentEpoch);
		this.bftEventProcessor.start();
	}

	private long currentEpoch() {
		return this.currentEpoch.getEpoch();
	}

	public void processLedgerUpdate(EpochsLedgerUpdate epochsLedgerUpdate) {
		epochsLedgerUpdate.getEpochChange().ifPresentOrElse(
			this::processEpochChange,
			() -> this.syncLedgerUpdateProcessor.processLedgerUpdate(epochsLedgerUpdate)
		);
	}

	private void processEpochChange(EpochChange epochChange) {
		// Sanity check
		if (epochChange.getEpoch() != this.currentEpoch() + 1) {
			throw new IllegalStateException("Bad Epoch change: " + epochChange + " current epoch: " + this.currentEpoch);
		}

		if (this.currentEpoch.getBFTConfiguration().getValidatorSet().containsNode(this.self)) {
			log.info("EPOCH_CHANGE: broadcasting next epoch");
			final ImmutableSet<BFTValidator> currentAndNextValidators =
					ImmutableSet.<BFTValidator>builder()
							.addAll(epochChange.getBFTConfiguration().getValidatorSet().getValidators())
							.addAll(this.currentEpoch.getBFTConfiguration().getValidatorSet().getValidators())
							.build();

			for (BFTValidator validator : currentAndNextValidators) {
				if (!validator.getNode().equals(self)) {
					epochsRPCSender.sendGetEpochResponse(validator.getNode(), epochChange.getProof());
				}
			}
		}

		log.trace("{}: EPOCH_CHANGE: {}", this.self, epochChange);

		this.currentEpoch = epochChange;
		this.updateEpochState();
		this.bftEventProcessor.start();

		// Execute any queued up consensus events
		final List<ConsensusEvent> queuedEventsForEpoch = queuedEvents.getOrDefault(epochChange.getEpoch(), Collections.emptyList());
		for (ConsensusEvent consensusEvent : queuedEventsForEpoch) {
			this.processConsensusEventInternal(consensusEvent);
		}

		numQueuedConsensusEvents -= queuedEventsForEpoch.size();
		counters.set(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS, numQueuedConsensusEvents);
		queuedEvents.remove(epochChange.getEpoch());
	}

	private void logEpochChange(EpochChange epochChange, String message) {
		if (log.isInfoEnabled()) {
			// Reduce complexity of epoch change log message, and make it easier to correlate with
			// other logs.  Size reduced from circa 6Kib to approx 1Kib over ValidatorSet.toString().
			BFTConfiguration configuration = epochChange.getBFTConfiguration();
			StringBuilder epochMessage = new StringBuilder(this.self.getSimpleName());
			epochMessage.append(": EPOCH_CHANGE: ");
			epochMessage.append(message);
			epochMessage.append(" new epoch ").append(epochChange.getEpoch());
			epochMessage.append(" with ").append(configuration.getValidatorSet().getValidators().size()).append(" validators: ");
			Iterator<BFTValidator> i = configuration.getValidatorSet().getValidators().iterator();
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

	public void processGetEpochRequest(GetEpochRequest request) {
		log.trace("{}: GET_EPOCH_REQUEST: {}", this.self, request);

		if (this.currentEpoch() > request.getEpoch()) {
			epochsRPCSender.sendGetEpochResponse(request.getAuthor(), this.currentEpoch.getProof());
		} else {
			log.warn("{}: GET_EPOCH_REQUEST: {} but currently on epoch: {}",
				this.self::getSimpleName, () -> request, this::currentEpoch
			);

			// TODO: Send better error message back
			epochsRPCSender.sendGetEpochResponse(request.getAuthor(), null);
		}
	}

	public void processGetEpochResponse(GetEpochResponse response) {
		log.trace("GET_EPOCH_RESPONSE: {}", response);

		if (response.getEpochProof() == null) {
			log.warn("Received empty GetEpochResponse {}", response);
			// TODO: retry
			return;
		}

		final VerifiedLedgerHeaderAndProof ancestor = response.getEpochProof();
		if (ancestor.getEpoch() >= this.currentEpoch()) {
			localSyncRequestProcessor.dispatch(new LocalSyncRequest(ancestor, ImmutableList.of(response.getAuthor())));
		} else {
			if (ancestor.getEpoch() + 1 < this.currentEpoch()) {
				log.info("Ignoring old epoch {} current {}", response, this.currentEpoch);
			}
		}
	}

	private void processConsensusEventInternal(ConsensusEvent consensusEvent) {
		if (consensusEvent instanceof ViewTimeout) {
			bftEventProcessor.processViewTimeout((ViewTimeout) consensusEvent);
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

	public void processLocalTimeout(Epoched<ScheduledLocalTimeout> localTimeout) {
		if (localTimeout.epoch() != this.currentEpoch()) {
			return;
		}

		bftEventProcessor.processLocalTimeout(localTimeout.event());
	}

	public EventProcessor<EpochViewUpdate> epochViewUpdateEventProcessor() {
		return epochViewUpdate -> {
			if (epochViewUpdate.getEpoch() != this.currentEpoch()) {
				return;
			}

			bftEventProcessor.processViewUpdate(epochViewUpdate.getViewUpdate());
		};
	}

	public void processBFTUpdate(BFTUpdate update) {
		bftUpdateProcessors.forEach(p -> p.process(update));
	}

	@Override
	public void processGetVerticesRequest(GetVerticesRequest request) {
		syncRequestProcessor.processGetVerticesRequest(request);
	}

	public void processGetVerticesLocalTimeout(LocalGetVerticesRequest request) {
		syncTimeoutProcessor.process(request);
	}

	public void processGetVerticesErrorResponse(GetVerticesErrorResponse response) {
		syncBFTResponseProcessor.processGetVerticesErrorResponse(response);
	}

	public void processGetVerticesResponse(GetVerticesResponse response) {
		syncBFTResponseProcessor.processGetVerticesResponse(response);
	}
}
