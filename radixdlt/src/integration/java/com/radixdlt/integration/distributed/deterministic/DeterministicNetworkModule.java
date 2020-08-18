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
import com.google.inject.name.Names;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.bft.VertexStore.SyncedVertexSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.integration.distributed.deterministic.network.DeterministicNetwork.DeterministicSender;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.SyncExecutor.CommittedStateSyncSender;

/**
 *
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
		bind(BFTEventSender.class).toInstance(this.sender);
		bind(SyncVerticesRPCSender.class).toInstance(this.sender);
		bind(SyncedVertexSender.class).toInstance(this.sender);
		bind(EpochChangeSender.class).toInstance(this.sender);
		bind(CommittedStateSyncSender.class).toInstance(this.sender);
		bind(LocalTimeoutSender.class).toInstance(this.sender);
		bind(SyncEpochsRPCSender.class).toInstance(this.sender);

		bind(NextCommandGenerator.class).toInstance((view, aids) -> null);
		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(TimeSupplier.class).toInstance(System::currentTimeMillis);

		bind(BFTNode.class).annotatedWith(Names.named("self")).toInstance(this.node);
	}
}
