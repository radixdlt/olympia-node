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

package com.radixdlt;

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.epoch.VertexStoreFactory;
import com.radixdlt.consensus.epoch.BFTSyncFactory;
import com.radixdlt.consensus.epoch.BFTSyncRequestProcessorFactory;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.liveness.NextTxnsGenerator;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.PacemakerStateFactory;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.environment.StartProcessorOnRunner;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import java.util.Comparator;
import java.util.Random;

/**
 * Module which allows for consensus to have multiple epochs
 */
public class EpochsConsensusModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(EpochManager.class).in(Scopes.SINGLETON);
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(EpochViewUpdate.class);
		eventBinder.addBinding().toInstance(VertexRequestTimeout.class);
		eventBinder.addBinding().toInstance(LedgerUpdate.class);
		eventBinder.addBinding().toInstance(Epoched.class);
	}

	@ProvidesIntoSet
	private StartProcessorOnRunner startProcessor(EpochManager epochManager) {
		return new StartProcessorOnRunner(
			Runners.CONSENSUS,
			epochManager::start
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> localVoteProcessor(EpochManager epochManager) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			Vote.class,
			epochManager::processConsensusEvent
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> remoteVoteProcessor(EpochManager epochManager) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			Vote.class,
			(node, vote) -> epochManager.processConsensusEvent(vote)
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> localProposalProcessor(EpochManager epochManager) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			Proposal.class,
			epochManager::processConsensusEvent
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> remoteProposalProcessor(EpochManager epochManager) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			Proposal.class,
			(node, proposal) -> epochManager.processConsensusEvent(proposal)
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> epochTimeoutProcessor(EpochManager epochManager) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { },
			epochManager::processLocalTimeout
		);
	}

    @ProvidesIntoSet
    private EventProcessorOnRunner<?> epochsLedgerUpdateEventProcessor(EpochManager epochManager) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			LedgerUpdate.class,
			epochManager.epochsLedgerUpdateEventProcessor()
		);
    }

    @ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> localGetVerticesRequestRemoteEventProcessor(EpochManager epochManager) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			GetVerticesRequest.class,
			epochManager.bftSyncRequestProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> responseRemoteEventProcessor(EpochManager epochManager) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			GetVerticesResponse.class,
			epochManager.bftSyncResponseProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> errorResponseRemoteEventProcessor(EpochManager epochManager) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			GetVerticesErrorResponse.class,
			epochManager.bftSyncErrorResponseProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> bftUpdateProcessor(EpochManager epochManager) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			BFTInsertUpdate.class,
			epochManager::processBFTUpdate
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> bftRebuildUpdateEventProcessor(EpochManager epochManager) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			BFTRebuildUpdate.class,
			epochManager.bftRebuildUpdateEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> bftSyncTimeoutProcessor(EpochManager epochManager) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			VertexRequestTimeout.class,
			epochManager.timeoutEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> epochViewUpdateEventProcessor(EpochManager epochManager) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			EpochViewUpdate.class,
			epochManager.epochViewUpdateEventProcessor()
		);
	}

	@Provides
	private EpochChange initialEpoch(
		@LastEpochProof LedgerProof proof,
		BFTConfiguration initialBFTConfig
	) {
		return new EpochChange(proof, initialBFTConfig);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<ScheduledLocalTimeout> initialEpochsTimeoutSender(
		ScheduledEventDispatcher<Epoched<ScheduledLocalTimeout>> localTimeoutSender,
		EpochChange initialEpoch
	) {
		return localTimeout -> {
			Epoched<ScheduledLocalTimeout> epochTimeout = Epoched.from(initialEpoch.getEpoch(), localTimeout);
			localTimeoutSender.dispatch(epochTimeout, localTimeout.millisecondsWaitTime());
		};
	}


	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<ViewUpdate> initialViewUpdateSender(
		EventDispatcher<EpochViewUpdate> epochViewUpdateEventDispatcher,
		EpochChange initialEpoch
	) {
		return viewUpdate -> {
			EpochViewUpdate epochViewUpdate = new EpochViewUpdate(initialEpoch.getEpoch(), viewUpdate);
			epochViewUpdateEventDispatcher.dispatch(epochViewUpdate);
		};
	}

	@Provides
	private PacemakerStateFactory pacemakerStateFactory(
		EventDispatcher<EpochViewUpdate> epochViewUpdateEventDispatcher
	) {
		return (initialView, epoch, proposerElection) ->
			new PacemakerState(initialView, proposerElection, viewUpdate -> {
				EpochViewUpdate epochViewUpdate = new EpochViewUpdate(epoch, viewUpdate);
				epochViewUpdateEventDispatcher.dispatch(epochViewUpdate);
			});
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	EventProcessor<LocalTimeoutOccurrence> initialEpochsTimeoutProcessor(
		EpochChange initialEpoch,
		EventDispatcher<EpochLocalTimeoutOccurrence> timeoutDispatcher
	) {
		return timeoutOccurrence ->
			timeoutDispatcher.dispatch(new EpochLocalTimeoutOccurrence(initialEpoch.getEpoch(), timeoutOccurrence));
	}

	@Provides
	private PacemakerFactory pacemakerFactory(
		@Self BFTNode self,
		SystemCounters counters,
		NextTxnsGenerator nextTxnsGenerator,
		Hasher hasher,
		EventDispatcher<EpochLocalTimeoutOccurrence> timeoutEventDispatcher,
		ScheduledEventDispatcher<Epoched<ScheduledLocalTimeout>> localTimeoutSender,
		RemoteEventDispatcher<Proposal> proposalDispatcher,
		RemoteEventDispatcher<Vote> voteDispatcher,
		TimeSupplier timeSupplier
	) {
		return (
			validatorSet,
			vertexStore,
			timeoutCalculator,
			safetyRules,
			initialViewUpdate,
			epoch
		) -> new Pacemaker(
			self,
			counters,
			validatorSet,
			vertexStore,
			safetyRules,
			timeout -> timeoutEventDispatcher.dispatch(new EpochLocalTimeoutOccurrence(epoch, timeout)),
			(scheduledTimeout, ms) -> localTimeoutSender.dispatch(Epoched.from(epoch, scheduledTimeout), ms),
			timeoutCalculator,
			nextTxnsGenerator,
			proposalDispatcher,
			voteDispatcher,
			hasher,
			timeSupplier,
			initialViewUpdate,
			counters
		);
	}

	@Provides
	private BFTSyncRequestProcessorFactory vertexStoreSyncVerticesRequestProcessorFactory(
		RemoteEventDispatcher<GetVerticesErrorResponse> errorResponseDispatcher,
		RemoteEventDispatcher<GetVerticesResponse> responseDispatcher
	) {
		return vertexStore -> new VertexStoreBFTSyncRequestProcessor(
			vertexStore,
			errorResponseDispatcher,
			responseDispatcher
		);
	}

	@Provides
	private BFTSyncFactory bftSyncFactory(
		RemoteEventDispatcher<GetVerticesRequest> requestSender,
		@Self BFTNode self,
		@GetVerticesRequestRateLimit RateLimiter syncRequestRateLimiter,
		EventDispatcher<LocalSyncRequest> syncLedgerRequestSender,
		ScheduledEventDispatcher<VertexRequestTimeout> timeoutDispatcher,
		Random random,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis,
		SystemCounters counters,
		Hasher hasher
	) {
		return (vertexStore, pacemakerState, configuration) -> new BFTSync(
			self,
			syncRequestRateLimiter,
			vertexStore,
			hasher,
			pacemakerState,
			Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
			requestSender,
			syncLedgerRequestSender,
			timeoutDispatcher,
			configuration.getVertexStoreState().getRootHeader(),
			random,
			bftSyncPatienceMillis,
			counters
		);
	}

	@Provides
	private VertexStoreFactory vertexStoreFactory(
		EventDispatcher<BFTInsertUpdate> updateSender,
		EventDispatcher<BFTRebuildUpdate> rebuildUpdateDispatcher,
		EventDispatcher<BFTHighQCUpdate> highQCUpdateEventDispatcher,
		EventDispatcher<BFTCommittedUpdate> committedDispatcher,
		Ledger ledger,
		Hasher hasher
	) {
		return vertexStoreState -> VertexStore.create(
			vertexStoreState,
			ledger,
			hasher,
			updateSender,
			rebuildUpdateDispatcher,
			highQCUpdateEventDispatcher,
			committedDispatcher
		);
	}
}
