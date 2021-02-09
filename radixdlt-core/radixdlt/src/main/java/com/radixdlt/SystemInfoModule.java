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

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.ProcessWithSystemInfoRunner;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.systeminfo.SystemInfoRunner;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.properties.RuntimeProperties;

import org.radix.Radix;

/**
 * Module which manages system info
 */
public class SystemInfoModule extends AbstractModule {
	private static final int DEFAULT_VERTEX_BUFFER_SIZE = 16;

	@Override
	protected void configure() {
		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(SystemInfoRunner.class).in(Scopes.SINGLETON);

		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
				.permitDuplicates();
		eventBinder.addBinding().toInstance(EpochViewUpdate.class);
		eventBinder.addBinding().toInstance(EpochLocalTimeoutOccurrence.class);
		eventBinder.addBinding().toInstance(BFTCommittedUpdate.class);
		eventBinder.addBinding().toInstance(BFTHighQCUpdate.class);
	}

	@ProvidesIntoSet
	private EventProcessor<EpochViewUpdate> epochViewEventProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return v -> inMemorySystemInfo.processView(v.getEpochView());
	}

	@ProvidesIntoSet
	private EventProcessor<EpochLocalTimeoutOccurrence> timeoutEventProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return inMemorySystemInfo::processTimeout;
	}

	@ProvidesIntoSet
	private EventProcessor<BFTCommittedUpdate> committedUpdateEventProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return inMemorySystemInfo.bftCommittedUpdateEventProcessor();
	}

	@ProvidesIntoSet
	@ProcessWithSystemInfoRunner
	private EventProcessor<BFTHighQCUpdate> highQCProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return inMemorySystemInfo.bftHighQCEventProcessor();
	}

	@Provides
	@Singleton
	private InMemorySystemInfo inMemorySystemInfo(RuntimeProperties runtimeProperties) {
		final int vertexBufferSize = runtimeProperties.get("api.debug.vertex_buffer_size", DEFAULT_VERTEX_BUFFER_SIZE);
		return new InMemorySystemInfo(vertexBufferSize);
	}

	@Provides
	@Singleton
	private InfoSupplier infoSupplier(
		SystemCounters counters,
		InMemorySystemInfo infoStateManager
	) {
		return () -> {
			EpochView currentEpochView = infoStateManager.getCurrentView();
			EpochLocalTimeoutOccurrence timeout = infoStateManager.getLastTimeout();
			QuorumCertificate highQC = infoStateManager.getHighestQC();

			return ImmutableMap.of(
				"epochManager", ImmutableMap.of(
					"highQC", highQC != null ? ImmutableMap.of(
						"epoch", highQC.getProposed().getLedgerHeader().getEpoch(),
						"view", highQC.getView().number(),
						"vertexId", highQC.getProposed().getVertexId()
					)
					: ImmutableMap.of(),
					"currentView", ImmutableMap.of(
						"epoch", currentEpochView.getEpoch(),
						"view", currentEpochView.getView().number()
					),
					"lastTimeout", timeout != null ? ImmutableMap.of(
						"epoch", timeout.getEpochView().getEpoch(),
						"view", timeout.getEpochView().getView().number(),
						"leader", timeout.getLeader().toString()
					)
					: ImmutableMap.of()
				),
				"counters", counters.toMap(),
				"system_version", Radix.systemVersionInfo()
			);
		};
	}
}
