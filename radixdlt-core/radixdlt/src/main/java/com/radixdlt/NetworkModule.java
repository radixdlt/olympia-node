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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.environment.rx.RxRemoteDispatcher;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.mempool.MempoolNetworkRx;
import com.radixdlt.mempool.MempoolNetworkTx;
import com.radixdlt.middleware2.network.MessageCentralBFTNetwork;
import com.radixdlt.middleware2.network.MessageCentralLedgerSync;
import com.radixdlt.middleware2.network.MessageCentralValidatorSync;
import com.radixdlt.middleware2.network.SimpleMempoolNetwork;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Network related module
 */
public final class NetworkModule extends AbstractModule {

	@Override
	protected void configure() {
		// provides (for SharedMempool)
		bind(SimpleMempoolNetwork.class).in(Scopes.SINGLETON);
		bind(MempoolNetworkRx.class).to(SimpleMempoolNetwork.class);
		bind(MempoolNetworkTx.class).to(SimpleMempoolNetwork.class);

		// Network BFT/Epoch Sync messages
		bind(MessageCentralValidatorSync.class).in(Scopes.SINGLETON);
		bind(SyncVerticesResponseSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncEpochsRPCSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncEpochsRPCRx.class).to(MessageCentralValidatorSync.class);
		bind(SyncVerticesRPCRx.class).to(MessageCentralValidatorSync.class);

		// Network BFT messages
		bind(MessageCentralBFTNetwork.class).in(Scopes.SINGLETON);
		bind(ProposalBroadcaster.class).to(MessageCentralBFTNetwork.class);
		bind(BFTEventsRx.class).to(MessageCentralBFTNetwork.class);
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> syncRequestDispatcher(MessageCentralBFTNetwork bftNetwork) {
		return RxRemoteDispatcher.create(Vote.class, bftNetwork.voteDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> vertexRequestDispatcher(MessageCentralValidatorSync messageCentralValidatorSync) {
		return RxRemoteDispatcher.create(GetVerticesRequest.class, messageCentralValidatorSync.verticesRequestDispatcher());
	}

	@Provides
	private RemoteEventDispatcher<DtoLedgerHeaderAndProof> syncRequestDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.syncRequestDispatcher();
	}

	@Provides
	private RemoteEventDispatcher<DtoCommandsAndProof> syncResponseDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.syncResponseDispatcher();
	}

	@Provides
	private Flowable<RemoteEvent<DtoCommandsAndProof>> syncResponses(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.syncResponses();
	}

	@Provides
	private Flowable<RemoteEvent<DtoLedgerHeaderAndProof>> syncRequests(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.syncRequests();
	}

	@Provides
	private Flowable<RemoteEvent<GetVerticesRequest>> vertexSyncRequests(MessageCentralValidatorSync validatorSync) {
		return validatorSync.requests();
	}

}
