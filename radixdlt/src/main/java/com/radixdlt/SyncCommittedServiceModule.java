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
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.middleware2.network.MessageCentralLedgerSync;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.sync.StateSyncNetwork;
import com.radixdlt.sync.SyncServiceProcessor;
import com.radixdlt.sync.SyncServiceProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.SyncServiceProcessor.SyncedCommandSender;
import com.radixdlt.sync.SyncServiceRunner;
import com.radixdlt.sync.SyncServiceRunner.LocalSyncRequestsRx;
import com.radixdlt.sync.SyncServiceRunner.SyncTimeoutsRx;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.sync.SyncServiceRunner.VersionUpdatesRx;
import com.radixdlt.universe.Universe;

/**
 * Module which manages synchronization of committed atoms across of nodes
 */
public class SyncCommittedServiceModule extends AbstractModule {
	private static final int BATCH_SIZE = 100;

	@Provides
	@Singleton
	private SyncServiceProcessor syncServiceProcessor(
		RadixEngineStateComputer executor,
		StateSyncNetwork stateSyncNetwork,
		AddressBook addressBook,
		SyncedCommandSender syncedCommandSender,
		SyncTimeoutScheduler syncTimeoutScheduler
	) {
		return new SyncServiceProcessor(
			executor,
			stateSyncNetwork,
			addressBook, syncedCommandSender,
			syncTimeoutScheduler,
			0,
			BATCH_SIZE,
			10
		);
	}

	@Provides
	@Singleton
	private SyncServiceRunner syncServiceRunner(
		LocalSyncRequestsRx localSyncRequestsRx,
		SyncTimeoutsRx syncTimeoutsRx,
		VersionUpdatesRx versionUpdatesRx,
		StateSyncNetwork stateSyncNetwork,
		SyncServiceProcessor syncServiceProcessor
	) {
		return new SyncServiceRunner(
			localSyncRequestsRx,
			syncTimeoutsRx,
			versionUpdatesRx,
			stateSyncNetwork,
			syncServiceProcessor
		);
	}

	@Provides
	@Singleton
	private SyncedCommandSender syncedAtomSender(StateComputerLedger stateComputerLedger) {
		return syncCmd -> stateComputerLedger.commit(syncCmd.getCommand(), syncCmd.getVertexMetadata());
	}

	@Provides
	@Singleton
	private StateSyncNetwork stateSyncNetwork(
		Universe universe,
		MessageCentral messageCentral
	) {
		return new MessageCentralLedgerSync(
			universe,
			messageCentral
		);
	}
}
