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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.bft.ViewVotingResult;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.Set;
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
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<LocalSyncRequest>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<LocalSyncRequest>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<Timeout>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<Timeout>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<EpochView>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<EpochView>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<BFTCommittedUpdate>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<BFTCommittedUpdate>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<ViewQuorumReached>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<Vote>>() { }, ProcessOnDispatch.class);
	}

	@Provides
	private EventDispatcher<LocalSyncRequest> localSyncRequestEventDispatcher(
		@Self BFTNode self,
		@ProcessOnDispatch Set<EventProcessor<LocalSyncRequest>> syncProcessors,
		Environment environment
	) {
		EventDispatcher<LocalSyncRequest> envDispatcher = environment.getDispatcher(LocalSyncRequest.class);
		return req -> {
			Class<?> callingClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
			logger.info("LOCAL_SYNC_REQUEST dispatched by {}", callingClass);

			if (req.getTargetNodes().contains(self)) {
				throw new IllegalStateException("Should not be targeting self.");
			}

			syncProcessors.forEach(e -> e.process(req));
			envDispatcher.dispatch(req);
		};
	}

	@Provides
	private ScheduledEventDispatcher<LocalGetVerticesRequest> localGetVerticesRequestRemoteEventDispatcher(Environment environment) {
		return environment.getScheduledDispatcher(LocalGetVerticesRequest.class);
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
	private EventDispatcher<BFTUpdate> viewEventDispatcher(
		Environment environment
	) {
		return environment.getDispatcher(BFTUpdate.class);
	}

	@Provides
	private EventDispatcher<EpochView> viewEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<EpochView>> processors,
		Set<EventProcessor<EpochView>> asyncProcessors,
		Environment environment
	) {
		if (asyncProcessors.isEmpty()) {
			return epochView -> processors.forEach(e -> e.process(epochView));
		} else {
			EventDispatcher<EpochView> dispatcher = environment.getDispatcher(EpochView.class);
			return epochView -> {
				dispatcher.dispatch(epochView);
				processors.forEach(e -> e.process(epochView));
			};
		}
	}

	@Provides
	private EventDispatcher<Timeout> timeoutEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<Timeout>> processors,
		Set<EventProcessor<Timeout>> asyncProcessors,
		Environment environment
	) {
		if (asyncProcessors.isEmpty()) {
			return timeout -> processors.forEach(e -> e.process(timeout));
		} else {
			EventDispatcher<Timeout> dispatcher = environment.getDispatcher(Timeout.class);
			return timeout -> {
				logger.warn("LOCAL_TIMEOUT dispatched: {}", timeout);
				processors.forEach(e -> e.process(timeout));
				dispatcher.dispatch(timeout);
			};
		}
	}

	@Provides
	private EventDispatcher<BFTCommittedUpdate> committedUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<BFTCommittedUpdate>> processors,
		Set<EventProcessor<BFTCommittedUpdate>> asyncProcessors,
		Environment environment
	) {
		if (asyncProcessors.isEmpty()) {
			return commit -> processors.forEach(e -> e.process(commit));
		} else {
			EventDispatcher<BFTCommittedUpdate> dispatcher = environment.getDispatcher(BFTCommittedUpdate.class);
			return commit -> {
				processors.forEach(e -> e.process(commit));
				dispatcher.dispatch(commit);
			};
		}
	}

	@Provides
	private RemoteEventDispatcher<Vote> voteDispatcher(
		@ProcessOnDispatch Set<EventProcessor<Vote>> processors,
		Environment environment
	) {
		RemoteEventDispatcher<Vote> dispatcher = environment.getRemoteDispatcher(Vote.class);
		return (node, vote) -> {
			logger.trace("Vote sending to {}: {}", node, vote);
			processors.forEach(e -> e.process(vote));
			dispatcher.dispatch(node, vote);
		};
	}
}
