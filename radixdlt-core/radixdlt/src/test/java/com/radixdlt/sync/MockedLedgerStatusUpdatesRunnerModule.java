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
 */

package com.radixdlt.sync;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.rx.ModuleRunnerImpl;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import io.reactivex.rxjava3.core.Flowable;

public final class MockedLedgerStatusUpdatesRunnerModule extends AbstractModule {
	/** mocked runner that only processes LedgerStatusUpdates; required for epoch change push message */
	@ProvidesIntoMap
	@StringMapKey("ledger-status-updates")
	@Singleton
	private ModuleRunner ledgerStatusUpdatesRunner(
		@Self BFTNode self,
		Flowable<RemoteEvent<LedgerStatusUpdate>> ledgerStatusUpdates,
		RemoteEventProcessor<LedgerStatusUpdate> ledgerStatusUpdateProcessor
	) {
		return ModuleRunnerImpl.builder()
			.add(ledgerStatusUpdates, ledgerStatusUpdateProcessor)
			.build("LedgerStatusUpdatesRunner " + self);
	}
}
