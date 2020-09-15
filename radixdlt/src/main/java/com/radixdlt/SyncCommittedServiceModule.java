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
import com.google.inject.multibindings.MapBinder;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.ledger.AccumulatorAndValidatorSetVerifier;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.LocalSyncServiceProcessor.DtoCommandsAndProofVerifier;
import com.radixdlt.sync.LocalSyncServiceProcessor.InvalidSyncedCommandsSender;
import com.radixdlt.sync.RemoteSyncServiceProcessor;
import com.radixdlt.sync.StateSyncNetwork;
import com.radixdlt.sync.LocalSyncServiceProcessor;
import com.radixdlt.sync.LocalSyncServiceProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.LocalSyncServiceProcessor.VerifiedSyncedCommandsSender;
import com.radixdlt.sync.SyncServiceRunner;
import java.util.Comparator;

/**
 * Module which manages synchronization of committed atoms across of nodes
 */
public class SyncCommittedServiceModule extends AbstractModule {
	private static final int BATCH_SIZE = 100;

	@Override
	public void configure() {
		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding("sync").to(SyncServiceRunner.class);
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
	private DtoCommandsAndProofVerifier verifier(
		LedgerAccumulatorVerifier verifier,
		BFTConfiguration initialConfiguration,
		Hasher hasher,
		HashVerifier hashVerifier
	) {
		return new AccumulatorAndValidatorSetVerifier(
			verifier,
			initialConfiguration.getValidatorSet(),
			hasher,
			hashVerifier
		);
	}

	@Provides
	@Singleton
	private LocalSyncServiceProcessor syncServiceProcessor(
		Comparator<VerifiedLedgerHeaderAndProof> headerComparator,
		VerifiedLedgerHeaderAndProof header,
		StateSyncNetwork stateSyncNetwork,
		VerifiedSyncedCommandsSender verifiedSyncedCommandsSender,
		InvalidSyncedCommandsSender invalidSyncedCommandsSender,
		SyncTimeoutScheduler syncTimeoutScheduler,
		DtoCommandsAndProofVerifier verifier
	) {
		return new LocalSyncServiceProcessor(
			stateSyncNetwork,
			verifiedSyncedCommandsSender,
			invalidSyncedCommandsSender,
			syncTimeoutScheduler,
			verifier,
			headerComparator,
			header,
			200
		);
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
