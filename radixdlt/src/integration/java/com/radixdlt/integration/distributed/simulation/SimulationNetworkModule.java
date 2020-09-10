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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.epoch.EmptySyncVerticesRPCSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.SimulatedNetworkImpl;
import com.radixdlt.network.TimeSupplier;

public class SimulationNetworkModule extends AbstractModule {
	private final boolean getVerticesRPCEnabled;
	private final SimulationNetwork simulationNetwork;

	public SimulationNetworkModule(
		boolean getVerticesRPCEnabled,
		SimulationNetwork simulationNetwork
	) {
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
		this.simulationNetwork = simulationNetwork;
	}

	@Override
	protected void configure() {
		bind(BFTEventsRx.class).to(SimulatedNetworkImpl.class);
		bind(SyncEpochsRPCRx.class).to(SimulatedNetworkImpl.class);
		bind(SyncVerticesRPCRx.class).to(SimulatedNetworkImpl.class);
		bind(BFTEventSender.class).to(SimulatedNetworkImpl.class);
		// TODO: Remove if
		if (getVerticesRPCEnabled) {
			bind(SyncVerticesRPCSender.class).to(SimulatedNetworkImpl.class);
		} else {
			bind(SyncVerticesRPCSender.class).toInstance(EmptySyncVerticesRPCSender.INSTANCE);
		}
		bind(SyncEpochsRPCSender.class).to(SimulatedNetworkImpl.class);

		// TODO: Move these out
		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
	}

	@Provides
	private SimulatedNetworkImpl network(@Named("self") BFTNode node) {
		return simulationNetwork.getNetwork(node);
	}
}
