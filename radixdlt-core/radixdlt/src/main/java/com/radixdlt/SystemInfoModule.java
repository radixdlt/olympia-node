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
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.counters.SystemCounters;

import org.radix.Radix;

/**
 * Module which manages system info
 */
public class SystemInfoModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(InMemorySystemInfo.class).in(Scopes.SINGLETON);
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
				.permitDuplicates();
		eventBinder.addBinding().toInstance(EpochViewUpdate.class);
		eventBinder.addBinding().toInstance(EpochLocalTimeoutOccurrence.class);
		eventBinder.addBinding().toInstance(BFTCommittedUpdate.class);
		eventBinder.addBinding().toInstance(BFTHighQCUpdate.class);
		eventBinder.addBinding().toInstance(EpochsLedgerUpdate.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> epochsLedgerUpdateProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return new EventProcessorOnRunner<>(
			Runners.SYSTEM_INFO,
			EpochsLedgerUpdate.class,
			inMemorySystemInfo.ledgerUpdateEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> epochViewEventProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return new EventProcessorOnRunner<>(
			Runners.SYSTEM_INFO,
			EpochViewUpdate.class,
			v -> inMemorySystemInfo.processView(v.getEpochView())
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> timeoutEventProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return new EventProcessorOnRunner<>(
			Runners.SYSTEM_INFO,
			EpochLocalTimeoutOccurrence.class,
			inMemorySystemInfo::processTimeout
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> committedUpdateEventProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return new EventProcessorOnRunner<>(
			Runners.SYSTEM_INFO,
			BFTCommittedUpdate.class,
			inMemorySystemInfo.bftCommittedUpdateEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> highQCProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return new EventProcessorOnRunner<>(
			Runners.SYSTEM_INFO,
			BFTHighQCUpdate.class,
			inMemorySystemInfo.bftHighQCEventProcessor()
		);
	}

	@Provides
	@Singleton
	private InfoSupplier infoSupplier(
		SystemCounters counters,
		InMemorySystemInfo infoStateManager,
		@PacemakerTimeout long pacemakerTimeout,
		@PacemakerRate double pacemakerRate,
		@PacemakerMaxExponent int pacemakerMaxExponent
	) {
		return () -> {
			EpochView currentEpochView = infoStateManager.getCurrentView();
			EpochLocalTimeoutOccurrence timeout = infoStateManager.getLastTimeout();
			QuorumCertificate highQC = infoStateManager.getHighestQC();

			return ImmutableMap.of(
				"configuration", ImmutableMap.of(
					"pacemakerTimeout", pacemakerTimeout,
					"pacemakerRate", pacemakerRate,
					"pacemakerMaxExponent", pacemakerMaxExponent
				),
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
						"leader", timeout.getLeader().toString(),
						"timeoutLengthMs", timeout.getBase().timeout().millisecondsWaitTime(),
						"count", timeout.getBase().timeout().count()
					)
					: ImmutableMap.of()
				),
				"counters", counters.toMap(),
				"system_version", Radix.systemVersionInfo()
			);
		};
	}
}
