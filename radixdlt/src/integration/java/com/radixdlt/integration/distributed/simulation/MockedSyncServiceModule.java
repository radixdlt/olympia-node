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
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.syncer.CommittedCommand;
import com.radixdlt.syncer.SyncExecutor.CommittedSender;
import com.radixdlt.syncer.SyncExecutor.SyncService;
import java.util.concurrent.ConcurrentMap;

public class MockedSyncServiceModule extends AbstractModule {
	private final ConcurrentMap<Long, CommittedCommand> sharedCommittedAtoms;

	public MockedSyncServiceModule(ConcurrentMap<Long, CommittedCommand> sharedCommittedAtoms) {
		this.sharedCommittedAtoms = sharedCommittedAtoms;
	}

	@ProvidesIntoSet
	private CommittedSender sync() {
		return (cmd, vertexMetadata) ->
			sharedCommittedAtoms.put(vertexMetadata.getStateVersion(), new CommittedCommand(cmd, vertexMetadata));
	}

	// TODO: change this to a service
	@ProvidesIntoSet
	SyncService syncService(
		SyncedExecutor syncedExecutor
	) {
		return request -> {
			final long targetVersion = request.getTarget().getStateVersion();
			final long initVersion = request.getCurrentVersion() + 1;
			for (long version = initVersion; version <= targetVersion; version++) {
				CommittedCommand committedCommand = sharedCommittedAtoms.get(version);
				syncedExecutor.commit(committedCommand.getCommand(), committedCommand.getVertexMetadata());
			}
		};
	}
}
