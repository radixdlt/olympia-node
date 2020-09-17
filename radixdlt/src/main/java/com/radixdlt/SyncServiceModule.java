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
import com.radixdlt.ledger.AccumulatorAndValidatorSetVerifier;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor.DtoCommandsAndProofVerifier;
import com.radixdlt.sync.AccumulatorRemoteSyncResponseVerifier;
import com.radixdlt.sync.AccumulatorRemoteSyncResponseVerifier.InvalidSyncedCommandsSender;
import com.radixdlt.sync.AccumulatorRemoteSyncResponseVerifier.VerifiedSyncedCommandsSender;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.LocalSyncServiceProcessor;
import com.radixdlt.sync.RemoteSyncServiceProcessor;
import com.radixdlt.sync.StateSyncNetwork;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor.SyncTimeoutScheduler;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Module which manages synchronization of committed atoms across of nodes
 */
public class SyncServiceModule extends AbstractModule {
	private static final int BATCH_SIZE = 100;

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
	private DtoCommandsAndProofVerifier initialVerifier(
		LedgerAccumulatorVerifier verifier,
		Hasher hasher,
		HashVerifier hashVerifier,
		BFTConfiguration initialConfiguration
	) {
		return new AccumulatorAndValidatorSetVerifier(
			verifier,
			initialConfiguration.getValidatorSet(),
			hasher,
			hashVerifier
		);
	}

	@Provides
	private Function<BFTConfiguration, AccumulatorRemoteSyncResponseVerifier> accumulatorVerifierFactory(
		VerifiedSyncedCommandsSender verifiedSyncedCommandsSender,
		InvalidSyncedCommandsSender invalidSyncedCommandsSender,
		LedgerAccumulatorVerifier verifier,
		Hasher hasher,
		HashVerifier hashVerifier
	) {
		return config -> {
			AccumulatorAndValidatorSetVerifier accumulatorAndValidatorSetVerifier = new AccumulatorAndValidatorSetVerifier(
				verifier,
				config.getValidatorSet(),
				hasher,
				hashVerifier
			);

			return new AccumulatorRemoteSyncResponseVerifier(
				verifiedSyncedCommandsSender,
				invalidSyncedCommandsSender,
				accumulatorAndValidatorSetVerifier
			);
		};
	}

	@Provides
	private Function<BFTConfiguration, LocalSyncServiceProcessor<LedgerUpdate>> localSyncFactory(
		Comparator<AccumulatorState> accumulatorComparator,
		StateSyncNetwork stateSyncNetwork,
		SyncTimeoutScheduler syncTimeoutScheduler
	) {
		return config -> {
			VerifiedLedgerHeaderAndProof header = config.getGenesisQC().getCommittedAndLedgerStateProof()
				.orElseThrow(RuntimeException::new).getSecond();

			return new AccumulatorLocalSyncServiceProcessor(
				stateSyncNetwork,
				syncTimeoutScheduler,
				accumulatorComparator,
				header,
				200
			);
		};
	}

	@Provides
	@Singleton
	private LocalSyncServiceProcessor<LedgerUpdate> syncServiceProcessor(
		BFTConfiguration initialConfiguration,
		Function<BFTConfiguration, LocalSyncServiceProcessor<LedgerUpdate>> factory
	) {
		return factory.apply(initialConfiguration);
	}

	@Provides
	private VerifiedSyncedCommandsSender syncedCommandSender(SystemCounters counters, Ledger ledger) {
		return cmds -> {
			counters.add(CounterType.SYNC_PROCESSED, cmds.getCommands().size());
			ledger.commit(cmds);
		};
	}

	@Provides
	private InvalidSyncedCommandsSender invalidCommandsSender(SystemCounters counters) {
		// TODO: Store bad commands for reference and later for slashing
		return commandsAndProof -> counters.increment(CounterType.SYNC_INVALID_COMMANDS_RECEIVED);
	}
}
