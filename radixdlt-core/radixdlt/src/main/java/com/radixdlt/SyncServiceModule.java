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
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.ScheduledEventProducerOnRunner;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.LocalSyncService.VerifiedSyncResponseSender;
import com.radixdlt.sync.LocalSyncService.InvalidSyncResponseSender;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.sync.SyncState;
import com.radixdlt.sync.RemoteSyncService;
import com.radixdlt.sync.LocalSyncService;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.validation.RemoteSyncResponseSignaturesVerifier;
import com.radixdlt.sync.validation.RemoteSyncResponseValidatorSetVerifier;

import java.time.Duration;

/**
 * Module which manages synchronization of committed atoms across of nodes
 */
public class SyncServiceModule extends AbstractModule {

	@Override
	public void configure() {
		bind(LocalSyncService.class).in(Scopes.SINGLETON);
		bind(RemoteSyncService.class).in(Scopes.SINGLETON);
	}

	@Provides
	private SyncState initialSyncState(@LastProof LedgerProof currentHeader) {
		return SyncState.IdleState.init(currentHeader);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> syncRequestEventProcessor(
		RemoteSyncService remoteSyncService
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.SYNC,
			SyncRequest.class,
			remoteSyncService.syncRequestEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> statusRequestEventProcessor(
		RemoteSyncService remoteSyncService
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.SYNC,
			StatusRequest.class,
			remoteSyncService.statusRequestEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> ledgerUpdateEventProcessor(
		RemoteSyncService remoteSyncService
	) {
		return new EventProcessorOnRunner<>(
			Runners.SYNC,
			LedgerUpdate.class,
			remoteSyncService.ledgerUpdateEventProcessor()
		);
	}

	@Provides
	private InvalidSyncResponseSender invalidSyncResponseSender(
		SystemCounters counters,
		PeerControl peerControl
	) {
		return (sender, resp) -> {
			peerControl.banPeer(NodeId.fromPublicKey(sender.getKey()), Duration.ofMinutes(10));
			counters.increment(CounterType.SYNC_INVALID_COMMANDS_RECEIVED);
		};
	}

	@Provides
	private VerifiedSyncResponseSender verifiedSyncResponseSender(
		EventDispatcher<VerifiedTxnsAndProof> syncCommandsDispatcher
	) {
		return resp -> {
			var txnsAndProof = resp.getTxnsAndProof();
			// TODO: Stateful ledger header verification:
			// TODO: -verify rootHash matches
			var nextHeader = new LedgerProof(
				txnsAndProof.getTail().getOpaque(),
				txnsAndProof.getTail().getLedgerHeader(),
				txnsAndProof.getTail().getSignatures()
			);

			var verified = VerifiedTxnsAndProof.create(
				txnsAndProof.getTxns(),
				nextHeader
			);

			syncCommandsDispatcher.dispatch(verified);
		};
	}

	@Provides
	private RemoteSyncResponseValidatorSetVerifier validatorSetVerifier(BFTConfiguration initialConfiguration) {
		return new RemoteSyncResponseValidatorSetVerifier(initialConfiguration.getValidatorSet());
	}

	@Provides
	private RemoteSyncResponseSignaturesVerifier signaturesVerifier(Hasher hasher, HashVerifier hashVerifier) {
		return new RemoteSyncResponseSignaturesVerifier(hasher, hashVerifier);
	}

	@ProvidesIntoSet
	public ScheduledEventProducerOnRunner<?> syncCheckTriggerEventProducer(
		EventDispatcher<SyncCheckTrigger> syncCheckTriggerEventDispatcher,
		SyncConfig syncConfig
	) {
		return new ScheduledEventProducerOnRunner<>(
			Runners.SYNC,
			syncCheckTriggerEventDispatcher,
			SyncCheckTrigger::create,
			Duration.ofMillis(syncConfig.syncCheckInterval()),
			Duration.ofMillis(syncConfig.syncCheckInterval())
		);
	}
}
