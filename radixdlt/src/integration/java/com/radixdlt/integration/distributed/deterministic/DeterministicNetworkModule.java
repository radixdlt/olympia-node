/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.deterministic;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.sync.BFTSync.BFTSyncTimeoutScheduler;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.ProceedToViewSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.epochs.EpochChangeManager.EpochsLedgerUpdateSender;
import com.radixdlt.integration.distributed.deterministic.network.DeterministicNetwork.DeterministicSender;
import com.radixdlt.network.TimeSupplier;

/**
 * Module that supplies network senders, as well as some other assorted
 * objects used to connect modules in the system.
 */
public class DeterministicNetworkModule extends AbstractModule {
	private final BFTNode node;
	private final DeterministicSender sender;

	public DeterministicNetworkModule(BFTNode node, DeterministicSender sender) {
		this.node = node;
		this.sender = sender;
	}

	@Override
	protected void configure() {
		EpochInfoSender emptyInfoSender = new EpochInfoSender() {
			@Override
			public void sendTimeoutProcessed(Timeout timeout) {
				// Ignore
			}

			@Override
			public void sendCurrentView(EpochView epochView) {
				// Ignore
			}
		};

		bind(DeterministicSender.class).toInstance(this.sender);

		bind(ProposalBroadcaster.class).to(DeterministicSender.class);
		bind(ProceedToViewSender.class).to(DeterministicSender.class);
		bind(SyncVerticesRequestSender.class).to(DeterministicSender.class);
		bind(SyncVerticesResponseSender.class).to(DeterministicSender.class);
		bind(BFTUpdateSender.class).to(DeterministicSender.class);
		bind(LocalTimeoutSender.class).to(DeterministicSender.class);
		bind(BFTSyncTimeoutScheduler.class).to(DeterministicSender.class);
		bind(SyncEpochsRPCSender.class).to(DeterministicSender.class);

		// TODO: Remove multibind?
		Multibinder.newSetBinder(binder(), VertexStoreEventSender.class).addBinding().to(DeterministicSender.class);
		Multibinder.newSetBinder(binder(), EpochsLedgerUpdateSender.class).addBinding().to(DeterministicSender.class);

		bind(EpochInfoSender.class).toInstance(emptyInfoSender);

		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(TimeSupplier.class).toInstance(System::currentTimeMillis);

		bind(BFTNode.class).annotatedWith(Self.class).toInstance(this.node);
	}
}
