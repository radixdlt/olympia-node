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
 *
 */

package com.radixdlt.network;

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
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
import com.radixdlt.environment.rx.RxRemoteEnvironment;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.middleware2.network.MessageCentralMempool;
import com.radixdlt.middleware2.network.MessageCentralValidatorSync;
import com.radixdlt.middleware2.network.MessageCentralBFTNetwork;
import com.radixdlt.middleware2.network.MessageCentralLedgerSync;
import com.radixdlt.network.addressbook.AddressBookPeersView;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Network related module
 */
public final class NetworkModule extends AbstractModule {

	@Override
	protected void configure() {
		// provides (for SharedMempool)
		bind(MessageCentralMempool.class).in(Scopes.SINGLETON);

		// Network BFT/Epoch Sync messages
		//TODO: make rate limits configurable
		bind(RateLimiter.class).annotatedWith(GetVerticesRequestRateLimit.class).toInstance(RateLimiter.create(50.0));

		bind(MessageCentralValidatorSync.class).in(Scopes.SINGLETON);
		bind(SyncVerticesResponseSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncEpochsRPCSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncEpochsRPCRx.class).to(MessageCentralValidatorSync.class);
		bind(SyncVerticesRPCRx.class).to(MessageCentralValidatorSync.class);

		// Network BFT messages
		bind(MessageCentralBFTNetwork.class).in(Scopes.SINGLETON);
		bind(ProposalBroadcaster.class).to(MessageCentralBFTNetwork.class);
		bind(BFTEventsRx.class).to(MessageCentralBFTNetwork.class);
		bind(PeersView.class).to(AddressBookPeersView.class);
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> mempoolAddedDispatcher(MessageCentralMempool messageCentralMempool) {
		return RxRemoteDispatcher.create(MempoolAdd.class, messageCentralMempool.commandRemoteEventDispatcher());
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
	private RemoteEventDispatcher<SyncRequest> syncRequestDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.syncRequestDispatcher();
	}

	@Provides
	private RemoteEventDispatcher<SyncResponse> syncResponseDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.syncResponseDispatcher();
	}

	@Provides
	private RemoteEventDispatcher<StatusRequest> statusRequestDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.statusRequestDispatcher();
	}

	@Provides
	private RemoteEventDispatcher<StatusResponse> statusResponseDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.statusResponseDispatcher();
	}

	@Provides
	private RemoteEventDispatcher<LedgerStatusUpdate> ledgerStatusUpdateDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.ledgerStatusUpdateDispatcher();
	}

	@Provides
	private Flowable<RemoteEvent<StatusRequest>> statusRequests(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.statusRequests();
	}

	@Provides
	private Flowable<RemoteEvent<StatusResponse>> statusResponses(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.statusResponses();
	}

	@Provides
	private Flowable<RemoteEvent<SyncRequest>> syncRequests(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.syncRequests();
	}

	@Provides
	private Flowable<RemoteEvent<SyncResponse>> syncResponses(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.syncResponses();
	}

	@Provides
	private Flowable<RemoteEvent<LedgerStatusUpdate>> ledgerStatusUpdates(MessageCentralLedgerSync messageCentralLedgerSync) {
		return messageCentralLedgerSync.ledgerStatusUpdates();
	}

	@Provides
	private Flowable<RemoteEvent<GetVerticesRequest>> vertexSyncRequests(MessageCentralValidatorSync validatorSync) {
		return validatorSync.requests();
	}

	// TODO: Clean this up, convert the rest of remote events into this
	@Provides
	@Singleton
	RxRemoteEnvironment rxRemoteEnvironment(MessageCentralMempool messageCentralMempool) {
	    return new RxRemoteEnvironment() {
			@Override
			public <T> Flowable<RemoteEvent<T>> remoteEvents(Class<T> remoteEventClass) {
				if (remoteEventClass != MempoolAdd.class) {
					throw new IllegalStateException();
				} else {
					return messageCentralMempool.mempoolComands().map(m -> (RemoteEvent<T>) m);
				}
			}
		};
	}
}
