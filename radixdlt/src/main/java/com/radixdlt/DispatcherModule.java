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
import com.radixdlt.consensus.LocalTimeoutOccurrence;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.FormedQC;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
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
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;
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
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<LocalTimeoutOccurrence>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<LocalTimeoutOccurrence>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<ViewUpdate>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<View>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<View>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<EpochView>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<EpochView>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<BFTCommittedUpdate>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<BFTCommittedUpdate>>() { }, ProcessOnDispatch.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<FormedQC>>() { }, ProcessOnDispatch.class);
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
	private ScheduledEventDispatcher<SyncInProgress> timeoutSync(Environment environment) {
		return environment.getScheduledDispatcher(SyncInProgress.class);
	}


	@Provides
	@Singleton
	private EventDispatcher<FormedQC> formedQCEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<FormedQC>> processors,
		SystemCounters systemCounters
	) {
		return formedQC -> {
			logger.trace("Formed QC: {}", formedQC.qc());
			systemCounters.increment(CounterType.BFT_VOTE_QUORUMS);
			processors.forEach(p -> p.process(formedQC));
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
	private EventDispatcher<LocalTimeoutOccurrence> timeoutEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<LocalTimeoutOccurrence>> processors,
		Set<EventProcessor<LocalTimeoutOccurrence>> asyncProcessors,
		Environment environment
	) {
		if (asyncProcessors.isEmpty()) {
			return timeout -> processors.forEach(e -> e.process(timeout));
		} else {
			EventDispatcher<LocalTimeoutOccurrence> dispatcher = environment.getDispatcher(LocalTimeoutOccurrence.class);
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

	@Provides
	private EventDispatcher<ViewUpdate> viewUpdateEventDispatcher(
		@ProcessOnDispatch Set<EventProcessor<ViewUpdate>> processors,
		Environment environment
	) {
		//EventDispatcher<ViewUpdate> dispatcher = environment.getDispatcher(ViewUpdate.class);
		return viewUpdate -> {
			processors.forEach(e -> e.process(viewUpdate));
			//dispatcher.dispatch(viewUpdate);
		};
	}

	@Provides
	private EventDispatcher<EpochViewUpdate> epochViewUpdateEventDispatcher(Environment environment) {
		return environment.getDispatcher(EpochViewUpdate.class);
	}

	@Provides
	private EventDispatcher<View> localConsensusTimeoutDispatcher(
		@ProcessOnDispatch Set<EventProcessor<View>> syncProcessors,
		Set<EventProcessor<View>> asyncProcessors,
		Environment environment
	) {
		if (asyncProcessors.isEmpty()) {
			return viewTimeout -> syncProcessors.forEach(e -> e.process(viewTimeout));
		} else {
			EventDispatcher<View> dispatcher = environment.getDispatcher(View.class);
			return commit -> {
				syncProcessors.forEach(e -> e.process(commit));
				dispatcher.dispatch(commit);
			};
		}
	}
}
