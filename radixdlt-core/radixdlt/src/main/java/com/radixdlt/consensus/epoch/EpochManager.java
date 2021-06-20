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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.PacemakerStateFactory;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
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
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages Epochs and the BFT instance (which is mostly epoch agnostic) associated with each epoch
 */
@NotThreadSafe
public final class EpochManager {
	private static final Logger log = LogManager.getLogger();
	private final BFTNode self;
	private final PacemakerFactory pacemakerFactory;
	private final VertexStoreFactory vertexStoreFactory;
	private final BFTSyncRequestProcessorFactory bftSyncRequestProcessorFactory;
	private final BFTSyncFactory bftSyncFactory;
	private final Hasher hasher;
	private final HashSigner signer;
	private final PacemakerTimeoutCalculator timeoutCalculator;
	private final SystemCounters counters;
	private final Map<Long, List<ConsensusEvent>> queuedEvents;
	private final BFTFactory bftFactory;
	private final PacemakerStateFactory pacemakerStateFactory;

	private EpochChange currentEpoch;

	private EventProcessor<VertexRequestTimeout> syncTimeoutProcessor;
	private EventProcessor<LedgerUpdate> syncLedgerUpdateProcessor;
	private BFTEventProcessor bftEventProcessor;

	private Set<RemoteEventProcessor<GetVerticesRequest>> syncRequestProcessors;
	private Set<RemoteEventProcessor<GetVerticesResponse>> syncResponseProcessors;
	private Set<RemoteEventProcessor<GetVerticesErrorResponse>> syncErrorResponseProcessors;

	private Set<EventProcessor<BFTInsertUpdate>> bftUpdateProcessors;
	private Set<EventProcessor<BFTRebuildUpdate>> bftRebuildProcessors;

	private final RemoteEventDispatcher<LedgerStatusUpdate> ledgerStatusUpdateDispatcher;

	private final PersistentSafetyStateStore persistentSafetyStateStore;

	@Inject
	public EpochManager(
		@Self BFTNode self,
		BFTEventProcessor initialBFTEventProcessor,
		VertexStoreBFTSyncRequestProcessor requestProcessor,
		BFTSync initialBFTSync,
		RemoteEventDispatcher<LedgerStatusUpdate> ledgerStatusUpdateDispatcher,
		EpochChange initialEpoch,
		PacemakerFactory pacemakerFactory,
		VertexStoreFactory vertexStoreFactory,
		BFTSyncFactory bftSyncFactory,
		BFTSyncRequestProcessorFactory bftSyncRequestProcessorFactory,
		BFTFactory bftFactory,
		SystemCounters counters,
		Hasher hasher,
		HashSigner signer,
		PacemakerTimeoutCalculator timeoutCalculator,
		PacemakerStateFactory pacemakerStateFactory,
		PersistentSafetyStateStore persistentSafetyStateStore
	) {
		var isValidator = initialEpoch.getBFTConfiguration().getValidatorSet().containsNode(self);
		// TODO: these should all be removed
		if (!isValidator) {
			this.bftEventProcessor =  EmptyBFTEventProcessor.INSTANCE;
			this.syncLedgerUpdateProcessor = update -> { };
			this.syncTimeoutProcessor = timeout -> { };
		} else {
			this.bftEventProcessor = Objects.requireNonNull(initialBFTEventProcessor);
			this.syncLedgerUpdateProcessor = initialBFTSync.baseLedgerUpdateEventProcessor();
			this.syncTimeoutProcessor = initialBFTSync.vertexRequestTimeoutEventProcessor();
		}
		this.syncResponseProcessors = isValidator ? Set.of(initialBFTSync.responseProcessor()) : Set.of();
		this.syncRequestProcessors = isValidator ? Set.of(requestProcessor) : Set.of();
		this.syncErrorResponseProcessors = isValidator ? Set.of(initialBFTSync.errorResponseProcessor()) : Set.of();
		this.bftUpdateProcessors = isValidator
			? Set.of(initialBFTSync::processBFTUpdate, initialBFTEventProcessor::processBFTUpdate)
			: Set.of();
		this.bftRebuildProcessors = isValidator
			? Set.of(initialBFTEventProcessor::processBFTRebuildUpdate)
			: Set.of();


		this.ledgerStatusUpdateDispatcher = Objects.requireNonNull(ledgerStatusUpdateDispatcher);
		this.currentEpoch = Objects.requireNonNull(initialEpoch);
		this.self = Objects.requireNonNull(self);
		this.pacemakerFactory = Objects.requireNonNull(pacemakerFactory);
		this.vertexStoreFactory = Objects.requireNonNull(vertexStoreFactory);
		this.bftSyncFactory = Objects.requireNonNull(bftSyncFactory);
		this.bftSyncRequestProcessorFactory = bftSyncRequestProcessorFactory;
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
			this.bftRebuildProcessors = Set.of();
			this.bftUpdateProcessors = Set.of();
			this.syncRequestProcessors = Set.of();
			this.syncResponseProcessors = Set.of();
			this.syncErrorResponseProcessors = Set.of();
			this.bftEventProcessor =  EmptyBFTEventProcessor.INSTANCE;
			this.syncLedgerUpdateProcessor = update -> { };
			this.syncTimeoutProcessor = timeout -> { };
			return;
		}

		final long nextEpoch = this.currentEpoch.getEpoch();
		logEpochChange(this.currentEpoch, "included in");

		// Config
		final BFTConfiguration bftConfiguration = this.currentEpoch.getBFTConfiguration();
		final ProposerElection proposerElection = bftConfiguration.getProposerElection();
		HighQC highQC = bftConfiguration.getVertexStoreState().getHighQC();
		View view = highQC.highestQC().getView().next();
		final BFTNode leader = proposerElection.getProposer(view);
		final BFTNode nextLeader = proposerElection.getProposer(view.next());
		final ViewUpdate initialViewUpdate = ViewUpdate.create(view, highQC, leader, nextLeader);

		// Mutable Consensus State
		final VertexStore vertexStore = vertexStoreFactory.create(bftConfiguration.getVertexStoreState());
		final PacemakerState pacemakerState = pacemakerStateFactory.create(initialViewUpdate, nextEpoch, proposerElection);

		// Consensus Drivers
		final SafetyRules safetyRules = new SafetyRules(self, SafetyState.initialState(), persistentSafetyStateStore, hasher, signer);
		final Pacemaker pacemaker = pacemakerFactory.create(
			validatorSet,
			vertexStore,
			timeoutCalculator,
			safetyRules,
			initialViewUpdate,
			nextEpoch
		);
		final BFTSync bftSync = bftSyncFactory.create(
			vertexStore,
			pacemakerState,
			bftConfiguration
		);

		this.syncLedgerUpdateProcessor = bftSync.baseLedgerUpdateEventProcessor();
		this.syncTimeoutProcessor = bftSync.vertexRequestTimeoutEventProcessor();

		this.bftEventProcessor = bftFactory.create(
			self,
			pacemaker,
			vertexStore,
			bftSync,
			bftSync.viewQuorumReachedEventProcessor(),
			validatorSet,
			initialViewUpdate,
			safetyRules
		);

		this.syncResponseProcessors = Set.of(bftSync.responseProcessor());
		this.syncErrorResponseProcessors = Set.of(bftSync.errorResponseProcessor());
		this.syncRequestProcessors = Set.of(bftSyncRequestProcessorFactory.create(vertexStore));
		this.bftRebuildProcessors = ImmutableSet.of(bftEventProcessor::processBFTRebuildUpdate);
		this.bftUpdateProcessors = ImmutableSet.of(bftSync::processBFTUpdate, bftEventProcessor::processBFTUpdate);
	}

	public void start() {
		log.info("EpochManager Start: {}", currentEpoch);
		this.bftEventProcessor.start();
	}

	private long currentEpoch() {
		return this.currentEpoch.getEpoch();
	}

	public EventProcessor<LedgerUpdate> epochsLedgerUpdateEventProcessor() {
		return this::processLedgerUpdate;
	}

	private void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		var epochChange = (Optional<EpochChange>) ledgerUpdate.getStateComputerOutput();
		epochChange.ifPresentOrElse(
			this::processEpochChange,
			() -> this.syncLedgerUpdateProcessor.process(ledgerUpdate)
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

			final var ledgerStatusUpdate = LedgerStatusUpdate.create(epochChange.getGenesisHeader());
			for (BFTValidator validator : currentAndNextValidators) {
				if (!validator.getNode().equals(self)) {
					this.ledgerStatusUpdateDispatcher.dispatch(validator.getNode(), ledgerStatusUpdate);
				}
			}
		}

		log.trace("EPOCH_CHANGE: {}", epochChange);

		this.currentEpoch = epochChange;
		this.updateEpochState();
		this.bftEventProcessor.start();

		// Execute any queued up consensus events
		final List<ConsensusEvent> queuedEventsForEpoch = queuedEvents.getOrDefault(epochChange.getEpoch(), Collections.emptyList());
		View highView = queuedEventsForEpoch.stream().map(ConsensusEvent::getView).max(Comparator.naturalOrder()).orElse(View.genesis());
		queuedEventsForEpoch.stream().filter(e -> e.getView().equals(highView))
			.forEach(this::processConsensusEventInternal);

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

	private void processConsensusEventInternal(ConsensusEvent consensusEvent) {
		this.counters.increment(CounterType.BFT_CONSENSUS_EVENTS);

		if (consensusEvent instanceof Proposal) {
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
			counters.increment(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS);
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

			log.trace("Processing ViewUpdate: {}", epochViewUpdate);
			bftEventProcessor.processViewUpdate(epochViewUpdate.getViewUpdate());
		};
	}

	public void processBFTUpdate(BFTInsertUpdate update) {
		bftUpdateProcessors.forEach(p -> p.process(update));
	}

	public EventProcessor<BFTRebuildUpdate> bftRebuildUpdateEventProcessor() {
		return update -> {
			if (update.getVertexStoreState().getRoot().getParentHeader().getLedgerHeader().getEpoch() != this.currentEpoch()) {
				return;
			}

			bftRebuildProcessors.forEach(p -> p.process(update));
		};
	}

	public RemoteEventProcessor<GetVerticesRequest> bftSyncRequestProcessor() {
		return (node, request) -> syncRequestProcessors.forEach(p -> p.process(node, request));
	}

	public RemoteEventProcessor<GetVerticesResponse> bftSyncResponseProcessor() {
		return (node, resp) -> syncResponseProcessors.forEach(p -> p.process(node, resp));
	}

	public RemoteEventProcessor<GetVerticesErrorResponse> bftSyncErrorResponseProcessor() {
		return (node, err) -> {
			log.debug("SYNC_ERROR: Received GetVerticesErrorResponse {}", err);
			final var responseEpoch = err.highQC().highestQC().getEpoch();
			if (responseEpoch < this.currentEpoch()) {
				log.debug("SYNC_ERROR: Ignoring lower epoch error response: {} current epoch: {}", err, this.currentEpoch());
				return;
			}
			if (responseEpoch > this.currentEpoch()) {
				log.debug("SYNC_ERROR: Received higher epoch error response: {} current epoch: {}", err, this.currentEpoch());
			} else {
				// Current epoch
				syncErrorResponseProcessors.forEach(p -> p.process(node, err));
			}
		};
	}

	public EventProcessor<VertexRequestTimeout> timeoutEventProcessor() {
		return this::processGetVerticesLocalTimeout;
	}

	private void processGetVerticesLocalTimeout(VertexRequestTimeout timeout) {
		syncTimeoutProcessor.process(timeout);
	}
}
