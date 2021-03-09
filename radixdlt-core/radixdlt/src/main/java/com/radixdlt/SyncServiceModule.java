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
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.LocalSyncService.VerifiedSyncResponseSender;
import com.radixdlt.sync.LocalSyncService.InvalidSyncResponseSender;
import com.radixdlt.sync.SyncState;
import com.radixdlt.sync.RemoteSyncService;
import com.radixdlt.sync.LocalSyncService;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.validation.RemoteSyncResponseSignaturesVerifier;
import com.radixdlt.sync.validation.RemoteSyncResponseValidatorSetVerifier;

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
	private SyncState initialSyncState(@LastProof VerifiedLedgerHeaderAndProof currentHeader) {
		return SyncState.IdleState.init(currentHeader);
	}

	@Provides
	private RemoteEventProcessor<SyncRequest> syncRequestEventProcessor(
		RemoteSyncService remoteSyncService
	) {
		return remoteSyncService.syncRequestEventProcessor();
	}

	@Provides
	private RemoteEventProcessor<StatusRequest> statusRequestEventProcessor(
		RemoteSyncService remoteSyncService
	) {
		return remoteSyncService.statusRequestEventProcessor();
	}

	@Provides
	private InvalidSyncResponseSender invalidSyncResponseSender(SystemCounters counters) {
		return resp -> counters.increment(CounterType.SYNC_INVALID_COMMANDS_RECEIVED);
	}

	@Provides
	private VerifiedSyncResponseSender verifiedSyncResponseSender(
		EventDispatcher<VerifiedCommandsAndProof> syncCommandsDispatcher
	) {
		return resp -> {
			DtoCommandsAndProof commandsAndProof = resp.getCommandsAndProof();
			// TODO: Stateful ledger header verification:
			// TODO: -verify rootHash matches
			VerifiedLedgerHeaderAndProof nextHeader = new VerifiedLedgerHeaderAndProof(
				commandsAndProof.getTail().getOpaque0(),
				commandsAndProof.getTail().getOpaque1(),
				commandsAndProof.getTail().getOpaque2(),
				commandsAndProof.getTail().getOpaque3(),
				commandsAndProof.getTail().getLedgerHeader(),
				commandsAndProof.getTail().getSignatures()
			);

			VerifiedCommandsAndProof verified = new VerifiedCommandsAndProof(
				commandsAndProof.getCommands(),
				nextHeader
			);

			syncCommandsDispatcher.dispatch(verified);
		};
	}

	@ProvidesIntoSet
	@ProcessWithSyncRunner
	private EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor(
		RemoteSyncService remoteSyncService
	) {
		return remoteSyncService.ledgerUpdateEventProcessor();
	}

	@Provides
	private RemoteSyncResponseValidatorSetVerifier validatorSetVerifier(BFTConfiguration initialConfiguration) {
		return new RemoteSyncResponseValidatorSetVerifier(initialConfiguration.getValidatorSet());
	}

	@Provides
	private RemoteSyncResponseSignaturesVerifier signaturesVerifier(Hasher hasher, HashVerifier hashVerifier) {
		return new RemoteSyncResponseSignaturesVerifier(hasher, hashVerifier);
	}
}
