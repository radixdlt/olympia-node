/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.environment;

import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.api.service.ScheduledCacheCleanup;
import com.radixdlt.api.service.ScheduledStatsCollecting;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.network.p2p.PendingOutboundChannelsManager.PeerOutboundConnectionTimeout;
import com.radixdlt.network.p2p.discovery.DiscoverPeers;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.discovery.GetPeers;
import com.radixdlt.network.p2p.discovery.PeersResponse;
import com.radixdlt.network.p2p.liveness.PeerPingTimeout;
import com.radixdlt.network.p2p.liveness.PeersLivenessCheckTrigger;
import com.radixdlt.network.p2p.liveness.Ping;
import com.radixdlt.network.p2p.liveness.Pong;
import com.radixdlt.statecomputer.REOutput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.api.chaos.mempoolfiller.ScheduledMempoolFill;
import com.radixdlt.api.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.api.chaos.messageflooder.ScheduledMessageFlood;
import com.radixdlt.api.data.ScheduledQueueFlush;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.NoVote;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.bft.ViewVotingResult;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.InvalidProposedTxn;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
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

import java.util.Set;

/**
 * Manages dispatching of internal events to a given environment
 * TODO: Move all other events into this module
 */
public class DispatcherModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();

	@Override
	public void configure() {
		bind(new TypeLiteral<EventDispatcher<NodeApplicationRequest>>() { })
			.toProvider(Dispatchers.dispatcherProvider(NodeApplicationRequest.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolAdd>>() { })
			.toProvider(Dispatchers.dispatcherProvider(MempoolAdd.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolAddSuccess>>() { })
			.toProvider(Dispatchers.dispatcherProvider(
				MempoolAddSuccess.class,
				m -> CounterType.MEMPOOL_ADD_SUCCESS
			)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolAddFailure>>() { })
			.toProvider(Dispatchers.dispatcherProvider(
				MempoolAddFailure.class,
				m -> CounterType.MEMPOOL_ERRORS_OTHER
			))
			.in(Scopes.SINGLETON);

		// TODO: Remove, this hack required for initial genesis event emit
		bind(new TypeLiteral<EventDispatcher<REOutput>>() { })
			.toProvider(Dispatchers.dispatcherProvider(REOutput.class)).in(Scopes.SINGLETON);

		bind(new TypeLiteral<EventDispatcher<AtomsRemovedFromMempool>>() { })
			.toProvider(Dispatchers.dispatcherProvider(AtomsRemovedFromMempool.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolRelayTrigger>>() { })
			.toProvider(Dispatchers.dispatcherProvider(MempoolRelayTrigger.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MessageFlooderUpdate>>() { })
			.toProvider(Dispatchers.dispatcherProvider(MessageFlooderUpdate.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<MempoolFillerUpdate>>() { })
			.toProvider(Dispatchers.dispatcherProvider(MempoolFillerUpdate.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<ScheduledMempoolFill>>() { })
			.toProvider(Dispatchers.dispatcherProvider(ScheduledMempoolFill.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<NoVote>>() { })
			.toProvider(Dispatchers.dispatcherProvider(NoVote.class, v -> CounterType.BFT_REJECTED))
			.in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<InvalidProposedTxn>>() { })
			.toProvider(Dispatchers.dispatcherProvider(
				InvalidProposedTxn.class,
				v -> CounterType.RADIX_ENGINE_INVALID_PROPOSED_COMMANDS
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
			.toProvider(Dispatchers.scheduledDispatcherProvider(SyncCheckReceiveStatusTimeout.class))
			.in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<SyncCheckTrigger>>() { })
			.toProvider(Dispatchers.dispatcherProvider(SyncCheckTrigger.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<ScheduledMempoolFill>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(ScheduledMempoolFill.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<ScheduledQueueFlush>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(ScheduledQueueFlush.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<ScheduledCacheCleanup>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(ScheduledCacheCleanup.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<ScheduledStatsCollecting>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(ScheduledStatsCollecting.class)).in(Scopes.SINGLETON);

		// BFT
		bind(new TypeLiteral<RemoteEventDispatcher<Proposal>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(Proposal.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<Vote>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(Vote.class)).in(Scopes.SINGLETON);

		// BFT Sync
		bind(new TypeLiteral<RemoteEventDispatcher<GetVerticesResponse>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(GetVerticesResponse.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<GetVerticesErrorResponse>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(GetVerticesErrorResponse.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<MempoolAdd>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(MempoolAdd.class)).in(Scopes.SINGLETON);

		final var scheduledTimeoutKey = new TypeLiteral<EventProcessor<ScheduledLocalTimeout>>() { };
		Multibinder.newSetBinder(binder(), scheduledTimeoutKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), scheduledTimeoutKey);

		final var syncRequestKey = new TypeLiteral<EventProcessor<LocalSyncRequest>>() { };
		Multibinder.newSetBinder(binder(), syncRequestKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), syncRequestKey);

		final var timeoutOccurrenceKey = new TypeLiteral<EventProcessor<LocalTimeoutOccurrence>>() { };
		Multibinder.newSetBinder(binder(), timeoutOccurrenceKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), timeoutOccurrenceKey);
		bind(new TypeLiteral<EventDispatcher<EpochLocalTimeoutOccurrence>>() { })
			.toProvider(Dispatchers.dispatcherProvider(EpochLocalTimeoutOccurrence.class)).in(Scopes.SINGLETON);

		final var viewUpdateKey = new TypeLiteral<EventProcessor<ViewUpdate>>() { };
		Multibinder.newSetBinder(binder(), viewUpdateKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), viewUpdateKey);

		bind(new TypeLiteral<EventDispatcher<EpochViewUpdate>>() { })
			.toProvider(Dispatchers.dispatcherProvider(EpochViewUpdate.class)).in(Scopes.SINGLETON);

		bind(new TypeLiteral<EventDispatcher<LedgerUpdate>>() { })
			.toProvider(Dispatchers.dispatcherProvider(LedgerUpdate.class)).in(Scopes.SINGLETON);

		final var insertUpdateKey = new TypeLiteral<EventProcessor<BFTInsertUpdate>>() { };
		Multibinder.newSetBinder(binder(), insertUpdateKey, ProcessOnDispatch.class);
		final var highQcUpdateKey = new TypeLiteral<EventProcessor<BFTHighQCUpdate>>() { };
		Multibinder.newSetBinder(binder(), highQcUpdateKey, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), highQcUpdateKey);
		final var committedUpdateKey = new TypeLiteral<EventProcessor<BFTCommittedUpdate>>() { };
		Multibinder.newSetBinder(binder(), committedUpdateKey);
		Multibinder.newSetBinder(binder(), committedUpdateKey, ProcessOnDispatch.class);
		final var syncUpdateKey = new TypeLiteral<EventProcessor<VerifiedTxnsAndProof>>() { };
		Multibinder.newSetBinder(binder(), syncUpdateKey, ProcessOnDispatch.class);

		final var verticesRequestKey = new TypeLiteral<EventProcessor<GetVerticesRequest>>() { };
		Multibinder.newSetBinder(binder(), verticesRequestKey, ProcessOnDispatch.class);

		bind(new TypeLiteral<EventDispatcher<ViewQuorumReached>>() { })
			.toProvider(Dispatchers.dispatcherProvider(
				ViewQuorumReached.class,
				v -> {
					if (v.votingResult() instanceof ViewVotingResult.FormedTC) {
						return CounterType.BFT_TIMEOUT_QUORUMS;
					}
					return CounterType.BFT_VOTE_QUORUMS;
				}
			));


		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnDispatch<?>>() { });

		configureP2p();
		configureSync();
	}

	private void configureP2p() {
		bind(new TypeLiteral<EventDispatcher<PeerEvent>>() { })
			.toProvider(Dispatchers.dispatcherProvider(PeerEvent.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<PeersLivenessCheckTrigger>>() { })
			.toProvider(Dispatchers.dispatcherProvider(PeersLivenessCheckTrigger.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<PeerPingTimeout>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(PeerPingTimeout.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<ScheduledEventDispatcher<PeerOutboundConnectionTimeout>>() { })
			.toProvider(Dispatchers.scheduledDispatcherProvider(PeerOutboundConnectionTimeout.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<Ping>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(Ping.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<Pong>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(Pong.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<GetPeers>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(GetPeers.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<RemoteEventDispatcher<PeersResponse>>() { })
			.toProvider(Dispatchers.remoteDispatcherProvider(PeersResponse.class)).in(Scopes.SINGLETON);
		bind(new TypeLiteral<EventDispatcher<DiscoverPeers>>() { })
			.toProvider(Dispatchers.dispatcherProvider(DiscoverPeers.class)).in(Scopes.SINGLETON);
	}

	private void configureSync() {
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
	}

	@Provides
	private EventDispatcher<LocalSyncRequest> localSyncRequestEventDispatcher(
		@Self BFTNode self,
		@ProcessOnDispatch Set<EventProcessor<LocalSyncRequest>> syncProcessors,
		Environment environment,
		SystemCounters systemCounters
	) {
		var envDispatcher = environment.getDispatcher(LocalSyncRequest.class);
		return req -> {
			if (logger.isTraceEnabled()) {
				var callingClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
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
		var dispatcher = environment.getScheduledDispatcher(ScheduledLocalTimeout.class);
		return (timeout, ms) -> {
			dispatcher.dispatch(timeout, ms);
			processors.forEach(e -> e.process(timeout));
		};
	}

	@Provides
	private EventDispatcher<BFTInsertUpdate> viewEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<BFTInsertUpdate>> processors,
		Environment environment,
		SystemCounters systemCounters
	) {
		var dispatcher = environment.getDispatcher(BFTInsertUpdate.class);
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
		var dispatcher = environment.getDispatcher(BFTRebuildUpdate.class);
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
		var dispatcher = environment.getDispatcher(BFTHighQCUpdate.class);
		return update -> {
			dispatcher.dispatch(update);
			processors.forEach(p -> p.process(update));
		};
	}

	@Provides
	private EventDispatcher<VerifiedTxnsAndProof> syncUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<VerifiedTxnsAndProof>> processors,
		SystemCounters systemCounters
	) {
		return commit -> {
			systemCounters.add(CounterType.SYNC_PROCESSED, commit.getTxns().size());
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
			var dispatcher = environment.getDispatcher(BFTCommittedUpdate.class);
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
			var dispatcher = environment.getDispatcher(LocalTimeoutOccurrence.class);
			return timeout -> {
				syncProcessors.forEach(e -> e.process(timeout));
				dispatcher.dispatch(timeout);
			};
		}
	}

	@Provides
	private RemoteEventDispatcher<GetVerticesRequest> verticesRequestDispatcher(
		@ProcessOnDispatch Set<EventProcessor<GetVerticesRequest>> processors,
		Environment environment,
		SystemCounters counters
	) {
		var dispatcher = environment.getRemoteDispatcher(GetVerticesRequest.class);
		return (node, request) -> {
			counters.increment(CounterType.BFT_SYNC_REQUESTS_SENT);
			dispatcher.dispatch(node, request);
			processors.forEach(e -> e.process(request));
		};
	}

	@Provides
	@Singleton
	private EventDispatcher<ViewUpdate> viewUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<ViewUpdate>> processors,
		Environment environment
	) {
		var dispatcher = environment.getDispatcher(ViewUpdate.class);
		return viewUpdate -> {
			processors.forEach(e -> e.process(viewUpdate));
			dispatcher.dispatch(viewUpdate);
		};
	}

}
