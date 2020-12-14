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
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.environment.rx.RxRemoteDispatcher;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.SimulatedNetworkImpl;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import io.reactivex.rxjava3.core.Observable;

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
		bind(ProposalBroadcaster.class).to(SimulatedNetworkImpl.class);
		bind(SyncEpochsRPCSender.class).to(SimulatedNetworkImpl.class);
		bind(SyncVerticesResponseSender.class).to(SimulatedNetworkImpl.class);
	}

	@Provides
	private SimulatedNetworkImpl network(@Self BFTNode node) {
		return simulationNetwork.getNetwork(node);
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> vertexRequestDispatcher(SimulatedNetworkImpl network) {
		return RxRemoteDispatcher.create(GetVerticesRequest.class, network.remoteEventDispatcher(GetVerticesRequest.class));
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> voteDispatcher(SimulatedNetworkImpl network) {
		return RxRemoteDispatcher.create(Vote.class, network.remoteEventDispatcher(Vote.class));
	}

	@Provides
	private RemoteEventDispatcher<DtoLedgerHeaderAndProof> syncRequestDispatcher(SimulatedNetworkImpl network) {
		return network.remoteEventDispatcher(DtoLedgerHeaderAndProof.class);
	}

	@Provides
	private RemoteEventDispatcher<DtoCommandsAndProof> syncResponseDispatcher(SimulatedNetworkImpl network) {
		return network.remoteEventDispatcher(DtoCommandsAndProof.class);
	}

	@Provides
	private Observable<RemoteEvent<GetVerticesRequest>> vertexRequests(SimulatedNetworkImpl network) {
		return network.remoteEvents(GetVerticesRequest.class);
	}

	@Provides
	private Observable<RemoteEvent<DtoLedgerHeaderAndProof>> syncRequests(SimulatedNetworkImpl network) {
		return network.remoteEvents(DtoLedgerHeaderAndProof.class);
	}

	@Provides
	private Observable<RemoteEvent<DtoCommandsAndProof>> syncResponses(SimulatedNetworkImpl network) {
		return network.remoteEvents(DtoCommandsAndProof.class);
	}
}
