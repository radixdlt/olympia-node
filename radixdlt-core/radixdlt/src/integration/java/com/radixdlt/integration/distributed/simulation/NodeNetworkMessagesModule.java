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
import com.radixdlt.environment.rx.RxRemoteEnvironment;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.SimulatedNetworkImpl;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import com.radixdlt.mempool.MempoolAdd;
import io.reactivex.rxjava3.core.Flowable;

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
		bind(RxRemoteEnvironment.class).to(SimulatedNetworkImpl.class).in(Scopes.SINGLETON);
	}

	@Provides
	private SimulatedNetworkImpl network(@Self BFTNode node) {
		return simulationNetwork.getNetwork(node);
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> mempoolAdd(SimulatedNetworkImpl network) {
		return RxRemoteDispatcher.create(MempoolAdd.class, network.remoteEventDispatcher(MempoolAdd.class));
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
	private RemoteEventDispatcher<SyncRequest> syncRequestDispatcher(SimulatedNetworkImpl network) {
		return network.remoteEventDispatcher(SyncRequest.class);
	}

	@Provides
	private RemoteEventDispatcher<SyncResponse> syncResponseDispatcher(SimulatedNetworkImpl network) {
		return network.remoteEventDispatcher(SyncResponse.class);
	}

	@Provides
	private RemoteEventDispatcher<StatusRequest> statusRequestDispatcher(SimulatedNetworkImpl network) {
		return network.remoteEventDispatcher(StatusRequest.class);
	}

	@Provides
	private RemoteEventDispatcher<StatusResponse> statusResponseDispatcher(SimulatedNetworkImpl network) {
		return network.remoteEventDispatcher(StatusResponse.class);
	}

	@Provides
	private Flowable<RemoteEvent<GetVerticesRequest>> vertexRequests(SimulatedNetworkImpl network) {
		return network.remoteEvents(GetVerticesRequest.class);
	}

	@Provides
	private Flowable<RemoteEvent<SyncRequest>> syncRequests(SimulatedNetworkImpl network) {
		return network.remoteEvents(SyncRequest.class);
	}

	@Provides
	private Flowable<RemoteEvent<SyncResponse>> syncResponses(SimulatedNetworkImpl network) {
		return network.remoteEvents(SyncResponse.class);
	}

	@Provides
	private Flowable<RemoteEvent<StatusRequest>> statusRequests(SimulatedNetworkImpl network) {
		return network.remoteEvents(StatusRequest.class);
	}

	@Provides
	private Flowable<RemoteEvent<StatusResponse>> statusResponses(SimulatedNetworkImpl network) {
		return network.remoteEvents(StatusResponse.class);
	}

}
