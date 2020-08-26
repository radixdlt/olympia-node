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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.concurrent.ConcurrentMap;

public class MockedSyncServiceModule extends AbstractModule {
	private final ConcurrentMap<Long, CommittedCommand> sharedCommittedAtoms;

	public MockedSyncServiceModule(ConcurrentMap<Long, CommittedCommand> sharedCommittedAtoms) {
		this.sharedCommittedAtoms = sharedCommittedAtoms;
	}

	@ProvidesIntoSet
	private CommittedSender sync() {
		return (cmd, vset) -> sharedCommittedAtoms.put(cmd.getVertexMetadata().getPreparedCommand().getStateVersion(), cmd);
	}

	@Provides
	@Singleton
	SyncRequestSender syncRequestSender(
		Ledger ledger
	) {
		return new SyncRequestSender() {
			long currentVersion = 1;

			@Override
			public void sendLocalSyncRequest(LocalSyncRequest request) {
				final long targetVersion = request.getTarget().getPreparedCommand().getStateVersion();
				for (long version = currentVersion; version <= targetVersion; version++) {
					CommittedCommand committedCommand = sharedCommittedAtoms.get(version);
					ledger.commit(committedCommand.getCommand(), committedCommand.getVertexMetadata());
				}
				currentVersion = targetVersion;
			}
		};
	}
}
