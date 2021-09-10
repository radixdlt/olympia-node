/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
		this.bftEventProcessor.start();
	}

	private long currentEpoch() {
		return this.currentEpoch.getEpoch();
	}

	public EventProcessor<LedgerUpdate> epochsLedgerUpdateEventProcessor() {
		return this::processLedgerUpdate;
	}

	private void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		var epochChange = ledgerUpdate.getStateComputerOutput().getInstance(EpochChange.class);
		if (epochChange != null) {
			this.processEpochChange(epochChange);
		} else {
			this.syncLedgerUpdateProcessor.process(ledgerUpdate);
		}
	}

	private void processEpochChange(EpochChange epochChange) {
		// Sanity check
		if (epochChange.getEpoch() != this.currentEpoch() + 1) {
			throw new IllegalStateException("Bad Epoch change: " + epochChange + " current epoch: " + this.currentEpoch);
		}

		if (this.currentEpoch.getBFTConfiguration().getValidatorSet().containsNode(this.self)) {
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
