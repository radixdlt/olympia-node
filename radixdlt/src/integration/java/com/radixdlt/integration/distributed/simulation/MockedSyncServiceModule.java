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

package com.radixdlt.integration.distributed.simulation;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.ledger.VerifiedCommittedCommands;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.LongStream;

public class MockedSyncServiceModule extends AbstractModule {
	private final ConcurrentMap<Long, Command> sharedCommittedCommands;

	public MockedSyncServiceModule(ConcurrentMap<Long, Command> sharedCommittedCommands) {
		this.sharedCommittedCommands = sharedCommittedCommands;
	}

	@ProvidesIntoSet
	private CommittedSender sync() {
		return (cmd, vset) -> cmd.forEach(sharedCommittedCommands::put);
	}

	@Provides
	@Singleton
	SyncRequestSender syncRequestSender(
		Ledger ledger
	) {
		return new SyncRequestSender() {
			long currentVersion = 0;

			@Override
			public void sendLocalSyncRequest(LocalSyncRequest request) {
				final long targetVersion = request.getTarget().getLedgerState().getStateVersion();
				ImmutableList<Command> commands = LongStream.range(currentVersion + 1, targetVersion + 1)
					.mapToObj(sharedCommittedCommands::get)
					.collect(ImmutableList.toImmutableList());
				ledger.commit(new VerifiedCommittedCommands(commands, request.getTarget()));
				currentVersion = targetVersion;
			}
		};
	}
}
