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
import com.google.common.collect.ImmutableSet.Builder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.rx.RxEnvironment;
import com.radixdlt.environment.rx.RxRemoteDispatcher;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
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
	private RxEnvironment rxEnvironment(
		ScheduledExecutorService ses,
		Set<RxRemoteDispatcher<?>> dispatchers
	) {
		Builder<Class<?>> eventClasses = ImmutableSet.builder();
		eventClasses.add(
			ScheduledLocalTimeout.class,
			LocalSyncRequest.class,
			GetVerticesRequest.class,
			VertexRequestTimeout.class,
			BFTInsertUpdate.class,
			BFTRebuildUpdate.class,
			BFTHighQCUpdate.class,
			BFTCommittedUpdate.class,
			LocalTimeoutOccurrence.class,
			EpochLocalTimeoutOccurrence.class,
			SyncInProgress.class,
			ViewUpdate.class,
			EpochViewUpdate.class,
			MempoolAddFailure.class,
			AtomCommittedToLedger.class
		);

		return new RxEnvironment(
			eventClasses.build(),
			ses,
			dispatchers
		);
	}

	@Provides
	Observable<MempoolAddFailure> mempoolAddFailures(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(MempoolAddFailure.class);
	}

	@Provides
	Observable<ScheduledLocalTimeout> localTimeouts(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(ScheduledLocalTimeout.class);
	}

	@Provides
	Observable<SyncInProgress> syncTimeouts(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(SyncInProgress.class);
	}

	@Provides
	Observable<BFTInsertUpdate> bftUpdates(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(BFTInsertUpdate.class);
	}

	@Provides
	Observable<BFTRebuildUpdate> bftRebuilds(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(BFTRebuildUpdate.class);
	}

	@Provides
	Observable<BFTHighQCUpdate> bftHighQCUpdates(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(BFTHighQCUpdate.class);
	}

	@Provides
	Observable<BFTCommittedUpdate> bftCommittedUpdates(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(BFTCommittedUpdate.class);
	}

	@Provides
	Observable<VertexRequestTimeout> vertexRequestTimeouts(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(VertexRequestTimeout.class);
	}

	@Provides
	Observable<LocalSyncRequest> syncRequests(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(LocalSyncRequest.class);
	}

	@Provides
	public Observable<LocalTimeoutOccurrence> timeouts(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(LocalTimeoutOccurrence.class);
	}

	@Provides
	public Observable<EpochLocalTimeoutOccurrence> epochTimeouts(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(EpochLocalTimeoutOccurrence.class);
	}

	@Provides
	public Observable<EpochViewUpdate> epochViewUpdates(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(EpochViewUpdate.class);
	}

	@Provides
	public Observable<ViewUpdate> viewUpdates(RxEnvironment rxEnvironment) {
		return rxEnvironment.getObservable(ViewUpdate.class);
	}
}
