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
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier.InvalidAccumulatorSender;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier.VerifiedAccumulatorSender;
import com.radixdlt.sync.RemoteSyncResponseSignaturesVerifier;
import com.radixdlt.sync.RemoteSyncResponseSignaturesVerifier.InvalidSignaturesSender;
import com.radixdlt.sync.RemoteSyncResponseSignaturesVerifier.VerifiedSignaturesSender;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.InvalidValidatorSetSender;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.VerifiedValidatorSetSender;
import com.radixdlt.sync.RemoteSyncServiceProcessor;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor;

/**
 * Module which manages synchronization of committed atoms across of nodes
 */
public class SyncServiceModule extends AbstractModule {
	private static final int BATCH_SIZE = 100;

	@Override
	public void configure() {
		bind(new TypeLiteral<RemoteEventProcessor<DtoLedgerHeaderAndProof>>() { }).to(RemoteSyncServiceProcessor.class);
		bind(LocalSyncServiceAccumulatorProcessor.class).in(Scopes.SINGLETON);
	}

	@Provides
	private VerifiedValidatorSetSender verifiedValidatorSetSender(RemoteSyncResponseSignaturesVerifier signaturesVerifier) {
		return signaturesVerifier::processSyncResponse;
	}

	@Provides
	private VerifiedSignaturesSender verifiedSignaturesSender(RemoteSyncResponseAccumulatorVerifier accumulatorVerifier) {
		return accumulatorVerifier::processSyncResponse;
	}

	@Provides
	private InvalidSignaturesSender invalidSignaturesSender(SystemCounters counters) {
		return resp -> counters.increment(CounterType.SYNC_INVALID_COMMANDS_RECEIVED);
	}

	@Provides
	private InvalidValidatorSetSender invalidValidatorSetSender(SystemCounters counters) {
		return resp -> counters.increment(CounterType.SYNC_INVALID_COMMANDS_RECEIVED);
	}

	@Provides
	private InvalidAccumulatorSender invalidAccumulatorSender(SystemCounters counters) {
		return resp -> counters.increment(CounterType.SYNC_INVALID_COMMANDS_RECEIVED);
	}

	@Provides
	private VerifiedAccumulatorSender verifiedSyncedCommandsSender(
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

	@Provides
	@Singleton
	private RemoteSyncServiceProcessor remoteSyncServiceProcessor(
		CommittedReader committedReader,
		RemoteEventDispatcher<DtoCommandsAndProof> syncResponseDispatcher,
		SystemCounters systemCounters
	) {
		return new RemoteSyncServiceProcessor(
			committedReader,
			syncResponseDispatcher,
			BATCH_SIZE,
			systemCounters
		);
	}

	@Provides
	RemoteSyncResponseValidatorSetVerifier validatorSetVerifier(
		VerifiedValidatorSetSender verifiedValidatorSetSender,
		InvalidValidatorSetSender invalidValidatorSetSender,
		BFTConfiguration initialConfiguration
	) {
		return new RemoteSyncResponseValidatorSetVerifier(
			verifiedValidatorSetSender,
			invalidValidatorSetSender,
			initialConfiguration.getValidatorSet()
		);
	}
}
