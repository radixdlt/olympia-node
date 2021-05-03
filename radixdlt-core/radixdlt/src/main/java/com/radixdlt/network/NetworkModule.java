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
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesResponse;
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

		// Network BFT messages
		bind(MessageCentralBFTNetwork.class).in(Scopes.SINGLETON);
		bind(PeersView.class).to(AddressBookPeersView.class);
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> mempoolAddDispatcher(MessageCentralMempool messageCentralMempool) {
		return RxRemoteDispatcher.create(MempoolAdd.class, messageCentralMempool.mempoolAddRemoteEventDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> proposalDispatcher(MessageCentralBFTNetwork bftNetwork) {
		return RxRemoteDispatcher.create(Proposal.class, bftNetwork.proposalDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> voteDispatcher(MessageCentralBFTNetwork bftNetwork) {
		return RxRemoteDispatcher.create(Vote.class, bftNetwork.voteDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> vertexRequestDispatcher(MessageCentralValidatorSync messageCentralValidatorSync) {
		return RxRemoteDispatcher.create(GetVerticesRequest.class, messageCentralValidatorSync.verticesRequestDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> vertexResponseDispatcher(MessageCentralValidatorSync messageCentralValidatorSync) {
		return RxRemoteDispatcher.create(GetVerticesResponse.class, messageCentralValidatorSync.verticesResponseDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> vertexErrorResponseDispatcher(MessageCentralValidatorSync messageCentralValidatorSync) {
		return RxRemoteDispatcher.create(
			GetVerticesErrorResponse.class,
			messageCentralValidatorSync.verticesErrorResponseDispatcher()
		);
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> syncRequestDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return RxRemoteDispatcher.create(SyncRequest.class, messageCentralLedgerSync.syncRequestDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> syncResponseDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return RxRemoteDispatcher.create(SyncResponse.class, messageCentralLedgerSync.syncResponseDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> statusRequestDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return RxRemoteDispatcher.create(StatusRequest.class, messageCentralLedgerSync.statusRequestDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> statusResponseDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return RxRemoteDispatcher.create(StatusResponse.class, messageCentralLedgerSync.statusResponseDispatcher());
	}

	@ProvidesIntoSet
	private RxRemoteDispatcher<?> ledgerStatusUpdateDispatcher(MessageCentralLedgerSync messageCentralLedgerSync) {
		return RxRemoteDispatcher.create(LedgerStatusUpdate.class, messageCentralLedgerSync.ledgerStatusUpdateDispatcher());
	}

	@Provides
	private Flowable<ConsensusEvent> localConsensusEvents(MessageCentralBFTNetwork bftNetwork) {
		return bftNetwork.localBftEvents();
	}

	@Provides
	private Flowable<RemoteEvent<ConsensusEvent>> remoteConsensusEvents(MessageCentralBFTNetwork bftNetwork) {
		return bftNetwork.remoteBftEvents();
	}

	@Provides
	private Flowable<RemoteEvent<GetVerticesRequest>> vertexSyncRequests(MessageCentralValidatorSync validatorSync) {
		return validatorSync.requests();
	}

	@Provides
	private Flowable<RemoteEvent<GetVerticesResponse>> vertexSyncResponses(MessageCentralValidatorSync validatorSync) {
		return validatorSync.responses();
	}

	@Provides
	private Flowable<RemoteEvent<GetVerticesErrorResponse>> bftSyncErrorResponses(MessageCentralValidatorSync validatorSync) {
		return validatorSync.errorResponses();
	}

	// TODO: Clean this up, convert the rest of remote events into this
	@Provides
	@Singleton
	@SuppressWarnings("unchecked")
	RxRemoteEnvironment rxRemoteEnvironment(
		MessageCentralMempool messageCentralMempool,
		MessageCentralLedgerSync messageCentralLedgerSync
	) {
	    return new RxRemoteEnvironment() {
			@Override
			public <T> Flowable<RemoteEvent<T>> remoteEvents(Class<T> remoteEventClass) {
				if (remoteEventClass == MempoolAdd.class) {
					return messageCentralMempool.mempoolComands().map(m -> (RemoteEvent<T>) m);
				} else if (remoteEventClass == SyncRequest.class) {
					return messageCentralLedgerSync.syncRequests().map(m -> (RemoteEvent<T>) m);
				} else if (remoteEventClass == SyncResponse.class) {
					return messageCentralLedgerSync.syncResponses().map(m -> (RemoteEvent<T>) m);
				} else if (remoteEventClass == StatusRequest.class) {
					return messageCentralLedgerSync.statusRequests().map(m -> (RemoteEvent<T>) m);
				} else if (remoteEventClass == StatusResponse.class) {
					return messageCentralLedgerSync.statusResponses().map(m -> (RemoteEvent<T>) m);
				} else if (remoteEventClass == LedgerStatusUpdate.class) {
					return messageCentralLedgerSync.ledgerStatusUpdates().map(m -> (RemoteEvent<T>) m);
				} else {
					throw new IllegalStateException();
				}
			}
		};
	}
}
