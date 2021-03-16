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

package com.radixdlt;

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.application.ValidatorRegistration;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.mempoolfiller.ScheduledMempoolFill;
import com.radixdlt.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.chaos.messageflooder.ScheduledMessageFlood;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.NoVote;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.bft.ViewVotingResult;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.Dispatchers;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.statecomputer.InvalidProposedCommand;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.statecomputer.RadixEngineMempoolException;
import java.util.Set;

import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages dispatching of internal events to a given environment
 * TODO: Move all other events into this module
 */
public class DispatcherModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();

	@Override
	public void configure() {
		bind(new TypeLiteral<EventDispatcher<ValidatorRegistration>>() { })
			.toProvider(Dispatchers.dispatcherProvider(ValidatorRegistration.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolAdd>>() { })
			.toProvider(Dispatchers.dispatcherProvider(MempoolAdd.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolAddSuccess>>() { })
			.toProvider(Dispatchers.dispatcherProvider(
				MempoolAddSuccess.class,
				m -> CounterType.MEMPOOL_ADD_SUCCESS,
				false
			)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolAddFailure>>() { })
			.toProvider(Dispatchers.dispatcherProvider(
				MempoolAddFailure.class,
				m -> {
					if (m.getException() instanceof RadixEngineMempoolException) {
						RadixEngineMempoolException e = (RadixEngineMempoolException) m.getException();
						if (e.getException().getErrorCode().equals(RadixEngineErrorCode.HOOK_ERROR)) {
							return CounterType.MEMPOOL_ERRORS_HOOK;
						} else if (e.getException().getErrorCode().equals(RadixEngineErrorCode.STATE_CONFLICT)) {
							return CounterType.MEMPOOL_ERRORS_CONFLICT;
						}
					}
					return CounterType.MEMPOOL_ERRORS_OTHER;
				},
				true
			))
			.in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<AtomsRemovedFromMempool>>() { })
				.toProvider(Dispatchers.dispatcherProvider(AtomsRemovedFromMempool.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<AtomCommittedToLedger>>() { })
			.toProvider(Dispatchers.dispatcherProvider(AtomCommittedToLedger.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MessageFlooderUpdate>>() { })
			.toProvider(Dispatchers.dispatcherProvider(MessageFlooderUpdate.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolFillerUpdate>>() { })
			.toProvider(Dispatchers.dispatcherProvider(MempoolFillerUpdate.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<ScheduledMempoolFill>>() { })
				.toProvider(Dispatchers.dispatcherProvider(ScheduledMempoolFill.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<NoVote>>() { })
			.toProvider(Dispatchers.dispatcherProvider(
				NoVote.class,
				v -> CounterType.BFT_REJECTED,
				true
			))
			.in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<InvalidProposedCommand>>() { })
			.toProvider(Dispatchers.dispatcherProvider(
				InvalidProposedCommand.class,
				v -> CounterType.RADIX_ENGINE_INVALID_PROPOSED_COMMANDS,
				true
			)).in(Scopes.SINGLETON);

		bind(new TypeLiteral<ScheduledEventDispatcher<Epoched<ScheduledLocalTimeout>>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(new TypeLiteral<Epoched<ScheduledLocalTimeout>>() { }))
			.in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<ScheduledMessageFlood>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(ScheduledMessageFlood.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<VertexRequestTimeout>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(VertexRequestTimeout.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<SyncRequestTimeout>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(SyncRequestTimeout.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<SyncLedgerUpdateTimeout>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(SyncLedgerUpdateTimeout.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(SyncCheckReceiveStatusTimeout.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<SyncCheckTrigger>>() { })
			.toProvider(Dispatchers.dispatcherProvider(SyncCheckTrigger.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<ScheduledMempoolFill>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(ScheduledMempoolFill.class)).in(Scopes.SINGLETON);

		bind(new TypeLiteral<RemoteEventDispatcher<MempoolAdd>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(
				MempoolAdd.class,
				CounterType.MEMPOOL_RELAYER_SENT_COUNT
			)).in(Scopes.SINGLETON);

		// Sync
		bind(new TypeLiteral<RemoteEventDispatcher<StatusRequest>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(StatusRequest.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<StatusResponse>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(StatusResponse.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<SyncRequest>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(SyncRequest.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<SyncResponse>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(SyncResponse.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<LedgerStatusUpdate>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(LedgerStatusUpdate.class)).in(Scopes.SINGLETON);

		final var scheduledTimeoutKey = new TypeLiteral<EventProcessor<ScheduledLocalTimeout>>() { };
		Multibinder.newSetBinder(binder(), scheduledTimeoutKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), scheduledTimeoutKey);

		final var syncRequestKey = new TypeLiteral<EventProcessor<LocalSyncRequest>>() { };
		Multibinder.newSetBinder(binder(), syncRequestKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), syncRequestKey);

		final var timeoutOccurrenceKey = new TypeLiteral<EventProcessor<LocalTimeoutOccurrence>>() { };
		Multibinder.newSetBinder(binder(), timeoutOccurrenceKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), timeoutOccurrenceKey);
		final var epochTimeoutOccurrenceKey = new TypeLiteral<EventProcessor<EpochLocalTimeoutOccurrence>>() { };
		Multibinder.newSetBinder(binder(), epochTimeoutOccurrenceKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), epochTimeoutOccurrenceKey);

		final var viewUpdateKey = new TypeLiteral<EventProcessor<ViewUpdate>>() { };
		Multibinder.newSetBinder(binder(), viewUpdateKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), viewUpdateKey);
		final var epochViewUpdateKey = new TypeLiteral<EventProcessor<EpochViewUpdate>>() { };
		Multibinder.newSetBinder(binder(), epochViewUpdateKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), epochViewUpdateKey);

		final var insertUpdateKey = new TypeLiteral<EventProcessor<BFTInsertUpdate>>() { };
		Multibinder.newSetBinder(binder(), insertUpdateKey, ProcessOnDispatch.class);
		final var highQcUpdateKey = new TypeLiteral<EventProcessor<BFTHighQCUpdate>>() { };
		Multibinder.newSetBinder(binder(), highQcUpdateKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), highQcUpdateKey);
		final var committedUpdateKey = new TypeLiteral<EventProcessor<BFTCommittedUpdate>>() { };
		Multibinder.newSetBinder(binder(), committedUpdateKey);
		Multibinder.newSetBinder(binder(), committedUpdateKey, ProcessOnDispatch.class);
		final var syncUpdateKey = new TypeLiteral<EventProcessor<VerifiedCommandsAndProof>>() { };
		Multibinder.newSetBinder(binder(), syncUpdateKey, ProcessOnDispatch.class);

		final var verticesRequestKey = new TypeLiteral<EventProcessor<GetVerticesRequest>>() { };
		Multibinder.newSetBinder(binder(), verticesRequestKey, ProcessOnDispatch.class);

		final var viewQuorumReachedKey = new TypeLiteral<EventProcessor<ViewQuorumReached>>() { };
		Multibinder.newSetBinder(binder(), viewQuorumReachedKey, ProcessOnDispatch.class);

		final var voteKey = new TypeLiteral<EventProcessor<Vote>>() { };
		Multibinder.newSetBinder(binder(), voteKey, ProcessOnDispatch.class);

		final var ledgerUpdateKey = new TypeLiteral<EventProcessor<LedgerUpdate>>() { };
		Multibinder.newSetBinder(binder(), ledgerUpdateKey, ProcessOnDispatch.class);

		final var epochsLedgerUpdateKey = new TypeLiteral<EventProcessor<EpochsLedgerUpdate>>() { };
		Multibinder.newSetBinder(binder(), epochsLedgerUpdateKey, ProcessOnDispatch.class);

		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnDispatch<?>>() { });
	}

	@Provides
	private EventDispatcher<LocalSyncRequest> localSyncRequestEventDispatcher(
		@Self BFTNode self,
		@ProcessOnDispatch Set<EventProcessor<LocalSyncRequest>> syncProcessors,
		Environment environment,
		SystemCounters systemCounters
	) {
		EventDispatcher<LocalSyncRequest> envDispatcher = environment.getDispatcher(LocalSyncRequest.class);
		return req -> {
			if (logger.isTraceEnabled()) {
				Class<?> callingClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
				logger.trace("LOCAL_SYNC_REQUEST dispatched by {}", callingClass);
			}

			if (req.getTargetNodes().contains(self)) {
				throw new IllegalStateException("Should not be targeting self.");
			}

			long stateVersion = systemCounters.get(CounterType.SYNC_TARGET_STATE_VERSION);
			if (req.getTarget().getStateVersion() > stateVersion) {
				systemCounters.set(CounterType.SYNC_TARGET_STATE_VERSION, req.getTarget().getStateVersion());
			}

			syncProcessors.forEach(e -> e.process(req));
			envDispatcher.dispatch(req);
		};
	}

	@Provides
	private ScheduledEventDispatcher<ScheduledLocalTimeout> scheduledTimeoutDispatcher(
		@ProcessOnDispatch Set<EventProcessor<ScheduledLocalTimeout>> processors,
		Environment environment
	) {
		ScheduledEventDispatcher<ScheduledLocalTimeout> dispatcher = environment.getScheduledDispatcher(ScheduledLocalTimeout.class);
		return (timeout, ms) -> {
			dispatcher.dispatch(timeout, ms);
			processors.forEach(e -> e.process(timeout));
		};
	}

	@Provides
	@Singleton
	private EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<ViewQuorumReached>> processors,
		SystemCounters systemCounters
	) {
		return viewQuorumReached -> {
			logger.trace("View quorum reached with result: {}", viewQuorumReached.votingResult());

			if (viewQuorumReached.votingResult() instanceof ViewVotingResult.FormedTC) {
				systemCounters.increment(CounterType.BFT_TIMEOUT_QUORUMS);
			} else if (viewQuorumReached.votingResult() instanceof ViewVotingResult.FormedQC) {
				systemCounters.increment(CounterType.BFT_VOTE_QUORUMS);
			}

			processors.forEach(p -> p.process(viewQuorumReached));
		};
	}

	@Provides
	private EventDispatcher<BFTInsertUpdate> viewEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<BFTInsertUpdate>> processors,
		Environment environment,
		SystemCounters systemCounters
	) {
		EventDispatcher<BFTInsertUpdate> dispatcher = environment.getDispatcher(BFTInsertUpdate.class);
		return update -> {
			if (update.getSiblingsCount() > 1) {
				systemCounters.increment(CounterType.BFT_VERTEX_STORE_FORKS);
			}
			if (!update.getInserted().getVertex().hasDirectParent()) {
				systemCounters.increment(CounterType.BFT_INDIRECT_PARENT);
			}
			systemCounters.set(CounterType.BFT_VERTEX_STORE_SIZE, update.getVertexStoreSize());
			dispatcher.dispatch(update);
			processors.forEach(p -> p.process(update));
		};
	}

	@Provides
	private EventDispatcher<BFTRebuildUpdate> bftRebuildUpdateEventDispatcher(
		Environment environment,
		SystemCounters systemCounters
	) {
		EventDispatcher<BFTRebuildUpdate> dispatcher = environment.getDispatcher(BFTRebuildUpdate.class);
		return update -> {
			long stateVersion = update.getVertexStoreState().getRootHeader().getStateVersion();
			systemCounters.set(CounterType.BFT_STATE_VERSION, stateVersion);
			systemCounters.set(CounterType.BFT_VERTEX_STORE_SIZE, update.getVertexStoreState().getVertices().size());
			systemCounters.increment(CounterType.BFT_VERTEX_STORE_REBUILDS);
			dispatcher.dispatch(update);
		};
	}

	@Provides
	private EventDispatcher<BFTHighQCUpdate> bftHighQCUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<BFTHighQCUpdate>> processors,
		Environment environment
	) {
		EventDispatcher<BFTHighQCUpdate> dispatcher = environment.getDispatcher(BFTHighQCUpdate.class);
		return update -> {
			dispatcher.dispatch(update);
			processors.forEach(p -> p.process(update));
		};
	}

	@Provides
	private EventDispatcher<VerifiedCommandsAndProof> syncUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<VerifiedCommandsAndProof>> processors,
		SystemCounters systemCounters
	) {
		return commit -> {
			systemCounters.add(CounterType.SYNC_PROCESSED, commit.getCommands().size());
			processors.forEach(e -> e.process(commit));
		};
	}

	@Provides
	private EventDispatcher<BFTCommittedUpdate> committedUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<BFTCommittedUpdate>> processors,
		Set<EventProcessor<BFTCommittedUpdate>> asyncProcessors,
		Environment environment,
		SystemCounters systemCounters
	) {
		if (asyncProcessors.isEmpty()) {
			return commit -> {
				long stateVersion = commit.getVertexStoreState().getRootHeader().getStateVersion();
				systemCounters.set(CounterType.BFT_STATE_VERSION, stateVersion);
				systemCounters.add(CounterType.BFT_PROCESSED, commit.getCommitted().size());
				systemCounters.set(CounterType.BFT_VERTEX_STORE_SIZE, commit.getVertexStoreSize());
				processors.forEach(e -> e.process(commit));
			};
		} else {
			EventDispatcher<BFTCommittedUpdate> dispatcher = environment.getDispatcher(BFTCommittedUpdate.class);
			return commit -> {
				long stateVersion = commit.getVertexStoreState().getRootHeader().getStateVersion();
				systemCounters.set(CounterType.BFT_STATE_VERSION, stateVersion);
				systemCounters.add(CounterType.BFT_PROCESSED, commit.getCommitted().size());
				systemCounters.set(CounterType.BFT_VERTEX_STORE_SIZE, commit.getVertexStoreSize());
				processors.forEach(e -> e.process(commit));
				dispatcher.dispatch(commit);
			};
		}
	}


	@Provides
	private EventDispatcher<LocalTimeoutOccurrence> localConsensusTimeoutDispatcher(
		@ProcessOnDispatch Set<EventProcessor<LocalTimeoutOccurrence>> syncProcessors,
		Set<EventProcessor<LocalTimeoutOccurrence>> asyncProcessors,
		Environment environment
	) {
		if (asyncProcessors.isEmpty()) {
			return viewTimeout -> syncProcessors.forEach(e -> e.process(viewTimeout));
		} else {
			EventDispatcher<LocalTimeoutOccurrence> dispatcher = environment.getDispatcher(LocalTimeoutOccurrence.class);
			return timeout -> {
				syncProcessors.forEach(e -> e.process(timeout));
				dispatcher.dispatch(timeout);
			};
		}
	}

	@Provides
	private EventDispatcher<EpochLocalTimeoutOccurrence> timeoutEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<EpochLocalTimeoutOccurrence>> processors,
		Set<EventProcessor<EpochLocalTimeoutOccurrence>> asyncProcessors,
		Environment environment
	) {
		if (asyncProcessors.isEmpty()) {
			return timeout -> {
				logger.info("LOCAL_TIMEOUT_OCCURRENCE: {}", timeout);
				processors.forEach(e -> e.process(timeout));
			};
		} else {
			EventDispatcher<EpochLocalTimeoutOccurrence> dispatcher = environment.getDispatcher(EpochLocalTimeoutOccurrence.class);
			return timeout -> {
				logger.info("LOCAL_TIMEOUT_OCCURRENCE: {}", timeout);
				dispatcher.dispatch(timeout);
				processors.forEach(e -> e.process(timeout));
			};
		}
	}

	@Provides
	private RemoteEventDispatcher<GetVerticesRequest> verticesRequestDispatcher(
		@ProcessOnDispatch Set<EventProcessor<GetVerticesRequest>> processors,
		Environment environment,
		SystemCounters counters
	) {
		RemoteEventDispatcher<GetVerticesRequest> dispatcher = environment.getRemoteDispatcher(GetVerticesRequest.class);
		return (node, request) -> {
			counters.increment(CounterType.BFT_SYNC_REQUESTS_SENT);
			dispatcher.dispatch(node, request);
			processors.forEach(e -> e.process(request));
		};
	}

	@Provides
	private RemoteEventDispatcher<Vote> voteDispatcher(
		@ProcessOnDispatch Set<EventProcessor<Vote>> processors,
		Environment environment
	) {
		RemoteEventDispatcher<Vote> dispatcher = environment.getRemoteDispatcher(Vote.class);
		return (node, vote) -> {
			logger.trace("Vote sending to {}: {}", node, vote);
			dispatcher.dispatch(node, vote);
			processors.forEach(e -> e.process(vote));
		};
	}

	@Provides
	@Singleton
	private EventDispatcher<ViewUpdate> viewUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<ViewUpdate>> processors,
		Environment environment
	) {
		EventDispatcher<ViewUpdate> dispatcher = environment.getDispatcher(ViewUpdate.class);
		final RateLimiter logLimiter = RateLimiter.create(1.0);
		return viewUpdate -> {
			Level logLevel = logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
			logger.log(logLevel, "NextSyncView: {}", viewUpdate);
			processors.forEach(e -> e.process(viewUpdate));
			dispatcher.dispatch(viewUpdate);
		};
	}

	@Provides
	@Singleton
	private EventDispatcher<EpochViewUpdate> epochViewUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<EpochViewUpdate>> processors,
		Environment environment
	) {
		final RateLimiter logLimiter = RateLimiter.create(1.0);
		EventDispatcher<EpochViewUpdate> dispatcher = environment.getDispatcher(EpochViewUpdate.class);
		return epochViewUpdate -> {
			Level logLevel = logLimiter.tryAcquire() ? Level.DEBUG : Level.TRACE;
			logger.log(logLevel, "NextSyncView: {}", epochViewUpdate);
			dispatcher.dispatch(epochViewUpdate);
			processors.forEach(e -> e.process(epochViewUpdate));
		};
	}

	@Provides
	@Singleton
	private EventDispatcher<LedgerUpdate> ledgerUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<LedgerUpdate>> processors,
		Environment environment
	) {
		EventDispatcher<LedgerUpdate> dispatcher = environment.getDispatcher(LedgerUpdate.class);
		return u -> {
			dispatcher.dispatch(u);
			processors.forEach(e -> e.process(u));
		};
	}

	@Provides
	@Singleton
	private EventDispatcher<EpochsLedgerUpdate> epochsLedgerUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<EpochsLedgerUpdate>> processors,
		Environment environment
	) {
		EventDispatcher<EpochsLedgerUpdate> dispatcher = environment.getDispatcher(EpochsLedgerUpdate.class);
		return u -> {
			dispatcher.dispatch(u);
			processors.forEach(e -> e.process(u));
		};
	}
}
