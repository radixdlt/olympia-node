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

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.LocalTimeoutOccurrence;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.rx.RxEnvironment;
import com.radixdlt.environment.rx.RxRemoteDispatcher;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;
import com.radixdlt.utils.ThreadFactories;
import io.reactivex.rxjava3.core.Observable;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Environment utilizing RxJava
 */
public class RxEnvironmentModule extends AbstractModule {

	@Override
	public void configure() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		bind(Environment.class).to(RxEnvironment.class);
		bind(ScheduledExecutorService.class).toInstance(ses);
	}

	@Provides
	@Singleton
	private RxEnvironment rxEnvironment(ScheduledExecutorService ses, Set<RxRemoteDispatcher<?>> dispatchers) {
		return new RxEnvironment(
			ImmutableSet.of(
				LocalSyncRequest.class,
				LocalGetVerticesRequest.class,
				BFTUpdate.class,
				BFTCommittedUpdate.class,
				EpochView.class,
				EpochViewUpdate.class,
				LocalTimeoutOccurrence.class,
				SyncInProgress.class
			),
			ses,
			dispatchers
		);
	}

	@Provides
	Observable<SyncInProgress> syncTimeouts(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(SyncInProgress.class);
	}

	@Provides
	Observable<BFTUpdate> bftUpdates(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(BFTUpdate.class);
	}

	@Provides
	Observable<BFTCommittedUpdate> bftCommittedUpdates(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(BFTCommittedUpdate.class);
	}

	@Provides
	Observable<LocalGetVerticesRequest> localGetVerticesRequests(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(LocalGetVerticesRequest.class);
	}

	@Provides
	Observable<LocalSyncRequest> syncRequests(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(LocalSyncRequest.class);
	}

	@Provides
	public Observable<EpochView> currentViews(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(EpochView.class);
	}

	@Provides
	public Observable<LocalTimeoutOccurrence> timeouts(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(LocalTimeoutOccurrence.class);
	}

	@Provides
	public Observable<EpochViewUpdate> viewUpdates(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(EpochViewUpdate.class);
	}
}
