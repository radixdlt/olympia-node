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

import org.radix.Radix;

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
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.middleware2.InfoSupplier;

import java.util.Map;

import static org.radix.Radix.SYSTEM_VERSION_KEY;

/**
 * Module which manages system info
 */
public class SystemInfoModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(InMemorySystemInfo.class).in(Scopes.SINGLETON);
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() {}, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(EpochViewUpdate.class);
		eventBinder.addBinding().toInstance(EpochLocalTimeoutOccurrence.class);
		eventBinder.addBinding().toInstance(BFTCommittedUpdate.class);
		eventBinder.addBinding().toInstance(BFTHighQCUpdate.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> epochsLedgerUpdateProcessor(InMemorySystemInfo inMemorySystemInfo) {
		return new EventProcessorOnRunner<>(
			Runners.SYSTEM_INFO,
			LedgerUpdate.class,
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
			var currentEpochView = infoStateManager.getCurrentView();
			var timeout = infoStateManager.getLastTimeout();
			var highQC = infoStateManager.getHighestQC();

			return Map.of(
				"configuration", Map.of(
					"pacemakerTimeout", pacemakerTimeout,
					"pacemakerRate", pacemakerRate,
					"pacemakerMaxExponent", pacemakerMaxExponent
				),
				"epochManager", Map.of(
					"highQC", highQC != null ? Map.of(
						"epoch", highQC.getProposed().getLedgerHeader().getEpoch(),
						"view", highQC.getView().number(),
						"vertexId", highQC.getProposed().getVertexId()
					) : Map.of(),
					"currentView", Map.of(
						"epoch", currentEpochView.getEpoch(),
						"view", currentEpochView.getView().number()
					),
					"lastTimeout", timeout != null ? Map.of(
						"epoch", timeout.getEpochView().getEpoch(),
						"view", timeout.getEpochView().getView().number(),
						"leader", timeout.getLeader().toString(),
						"timeoutLengthMs", timeout.getBase().timeout().millisecondsWaitTime(),
						"count", timeout.getBase().timeout().count()
					) : Map.of()
				),
				"counters", counters.toMap(),
				SYSTEM_VERSION_KEY, Radix.systemVersionInfo().get(SYSTEM_VERSION_KEY)
			);
		};
	}
}
