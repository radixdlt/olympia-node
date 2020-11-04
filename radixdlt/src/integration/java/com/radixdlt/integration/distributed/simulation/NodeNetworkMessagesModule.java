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
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.liveness.VoteSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.SimulatedNetworkImpl;
import com.radixdlt.sync.StateSyncNetworkRx;
import com.radixdlt.sync.StateSyncNetworkSender;

public class NodeNetworkMessagesModule extends AbstractModule {
	private final SimulationNetwork simulationNetwork;

	public NodeNetworkMessagesModule(SimulationNetwork simulationNetwork) {
		this.simulationNetwork = simulationNetwork;
	}

	@Override
	protected void configure() {
		bind(BFTEventsRx.class).to(SimulatedNetworkImpl.class);
		bind(SyncEpochsRPCRx.class).to(SimulatedNetworkImpl.class);
		bind(SyncVerticesRPCRx.class).to(SimulatedNetworkImpl.class);
		bind(StateSyncNetworkRx.class).to(SimulatedNetworkImpl.class);
		bind(ProposalBroadcaster.class).to(SimulatedNetworkImpl.class);
		bind(VoteSender.class).to(SimulatedNetworkImpl.class);
		bind(StateSyncNetworkSender.class).to(SimulatedNetworkImpl.class);
		bind(SyncVerticesRequestSender.class).to(SimulatedNetworkImpl.class);
		bind(SyncEpochsRPCSender.class).to(SimulatedNetworkImpl.class);
		bind(SyncVerticesResponseSender.class).to(SimulatedNetworkImpl.class);
	}

	@Provides
	private SimulatedNetworkImpl network(@Self BFTNode node) {
		return simulationNetwork.getNetwork(node);
	}
}
