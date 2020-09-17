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
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.ledger.AccumulatorAndValidatorSetVerifier;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.AccumulatorRemoteSyncResponseVerifier;
import com.radixdlt.sync.InvalidSyncedCommandsSender;
import com.radixdlt.sync.VerifiedSyncedCommandsSender;
import com.radixdlt.sync.LocalSyncServiceProcessor;
import com.radixdlt.sync.StateSyncNetwork;
import java.util.Comparator;
import java.util.function.Function;

public class EpochsSyncModule extends AbstractModule {

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

}
