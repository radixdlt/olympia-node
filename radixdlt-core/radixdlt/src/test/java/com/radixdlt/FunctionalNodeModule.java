/*
 * (C) Copyright 2021 Radix DLT Ltd
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
import com.google.inject.Module;
import com.radixdlt.application.validator.ValidatorRegistratorModule;
import com.radixdlt.ledger.MockedCommandGeneratorModule;
import com.radixdlt.ledger.MockedLedgerModule;
import com.radixdlt.mempool.MempoolReceiverModule;
import com.radixdlt.mempool.MempoolRelayerModule;
import com.radixdlt.statecomputer.MockedMempoolStateComputerModule;
import com.radixdlt.statecomputer.MockedStateComputerModule;
import com.radixdlt.statecomputer.MockedStateComputerWithEpochsModule;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.sync.MockedSyncServiceModule;

/**
 * Manages the functional components of a node
 */
public final class FunctionalNodeModule extends AbstractModule {
	private final boolean hasConsensus;
	private final boolean hasSync;

	// State manager
	private final boolean hasLedger;
	private final boolean hasMempool;
	private final boolean hasRadixEngine;

	private final boolean hasMempoolRelayer;

	private final boolean hasEpochs;

	// FIXME: This is required for now for shared syncing, remove after refactor
	private final Module mockedSyncServiceModule = new MockedSyncServiceModule();

	public FunctionalNodeModule() {
		this(true, true, true, true, true, true, true);
	}

	public FunctionalNodeModule(
		boolean hasConsensus,
		boolean hasLedger,
		boolean hasMempool,
		boolean hasMempoolRelayer,
		boolean hasRadixEngine,
		boolean hasEpochs,
		boolean hasSync
	) {
		this.hasConsensus = hasConsensus;
		this.hasLedger = hasLedger;
		this.hasMempool = hasMempool;
		this.hasMempoolRelayer = hasMempoolRelayer;
		this.hasRadixEngine = hasRadixEngine;
		this.hasEpochs = hasEpochs;
		this.hasSync = hasSync;
	}

	@Override
	public void configure() {
		install(new DispatcherModule());

		// Consensus
		if (hasConsensus) {
			install(new ConsensusModule());
			if (hasEpochs) {
				install(new EpochsConsensusModule());
			}
		}

		// Sync
		if (hasLedger) {
			if (!hasSync) {
				install(mockedSyncServiceModule);
			} else {
				install(new SyncServiceModule());
				if (hasEpochs) {
					install(new EpochsSyncModule());
				}
			}
		}

		// State Manager
		if (!hasLedger) {
			install(new MockedLedgerModule());
		} else {
			install(new LedgerModule());

			if (!hasMempool) {
				install(new MockedCommandGeneratorModule());

				if (!hasEpochs) {
					install(new MockedStateComputerModule());
				} else {
					install(new MockedStateComputerWithEpochsModule());
				}
			} else {
				install(new MempoolReceiverModule());

				if (hasMempoolRelayer) {
					install(new MempoolRelayerModule());
				}

				if (!hasRadixEngine) {
					install(new MockedMempoolStateComputerModule());
				} else {
					install(new NoFeeModule());
					install(new RadixEngineModule());
					install(new ValidatorRegistratorModule());
					install(new RadixEngineCheckpointModule());
				}
			}

			if (hasEpochs) {
				install(new EpochsLedgerUpdateModule());
			}
		}
	}
}
