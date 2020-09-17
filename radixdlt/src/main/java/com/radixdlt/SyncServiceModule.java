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
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.LocalSyncServiceProcessor;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier.InvalidAccumulatorSender;
import com.radixdlt.sync.RemoteSyncResponseAccumulatorVerifier.VerifiedAccumulatorSender;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.InvalidValidatorSetSender;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.VerifiedValidatorSetSender;
import com.radixdlt.sync.RemoteSyncServiceProcessor;
import com.radixdlt.sync.StateSyncNetwork;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncTimeoutScheduler;
import java.util.Comparator;

/**
 * Module which manages synchronization of committed atoms across of nodes
 */
public class SyncServiceModule extends AbstractModule {
	private static final int BATCH_SIZE = 100;

	@Provides
	private VerifiedValidatorSetSender verifiedValidatorSetSender(RemoteSyncResponseAccumulatorVerifier accumulatorVerifier) {
		return accumulatorVerifier::processSyncResponse;
	}

	@Provides
	private InvalidValidatorSetSender invalidValidatorSetSender(SystemCounters counters) {
		return (resp, msg) -> counters.increment(CounterType.SYNC_INVALID_COMMANDS_RECEIVED);
	}

	@Provides
	private InvalidAccumulatorSender invalidAccumulatorSender(SystemCounters counters) {
		return resp -> counters.increment(CounterType.SYNC_INVALID_COMMANDS_RECEIVED);
	}

	@Provides
	private VerifiedAccumulatorSender verifiedSyncedCommandsSender(SystemCounters counters, Ledger ledger) {
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

			counters.add(CounterType.SYNC_PROCESSED, verified.getCommands().size());
			ledger.commit(verified);
		};
	}

	@Provides
	@Singleton
	private RemoteSyncServiceProcessor remoteSyncServiceProcessor(
		CommittedReader committedReader,
		StateSyncNetwork stateSyncNetwork
	) {
		return new RemoteSyncServiceProcessor(
			committedReader,
			stateSyncNetwork,
			BATCH_SIZE
		);
	}

	@Provides
	@Singleton
	RemoteSyncResponseValidatorSetVerifier validatorSetVerifier(
		VerifiedValidatorSetSender verifiedValidatorSetSender,
		InvalidValidatorSetSender invalidValidatorSetSender,
		Hasher hasher,
		HashVerifier hashVerifier,
		BFTConfiguration initialConfiguration
	) {
		return new RemoteSyncResponseValidatorSetVerifier(
			verifiedValidatorSetSender,
			invalidValidatorSetSender,
			initialConfiguration.getValidatorSet(),
			hasher,
			hashVerifier
		);
	}

	@Provides
	@Singleton
	private LocalSyncServiceProcessor<LedgerUpdate> localSyncServiceProcessor(
		Comparator<AccumulatorState> accumulatorComparator,
		StateSyncNetwork stateSyncNetwork,
		SyncTimeoutScheduler syncTimeoutScheduler,
		BFTConfiguration initialConfiguration
	) {
		VerifiedLedgerHeaderAndProof header = initialConfiguration.getGenesisQC().getCommittedAndLedgerStateProof()
			.orElseThrow(RuntimeException::new).getSecond();

		return new LocalSyncServiceAccumulatorProcessor(
			stateSyncNetwork,
			syncTimeoutScheduler,
			accumulatorComparator,
			header,
			200
		);
	}
}
