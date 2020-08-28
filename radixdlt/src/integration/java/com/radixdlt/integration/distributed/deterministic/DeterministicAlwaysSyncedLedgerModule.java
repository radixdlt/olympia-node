/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.deterministic;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.sync.SyncRequestSender;

/**
 * A synced executor that is always synced.
 */
public class DeterministicAlwaysSyncedLedgerModule extends AbstractModule {
	@Provides
	private SyncRequestSender syncRequestSender() {
		return request -> { };
	}

	@Singleton
	@ProvidesIntoSet
	Ledger syncedExecutor() {
		return new Ledger() {
			@Override
			public PreparedCommand prepare(Vertex vertex) {
				return PreparedCommand.create(0, 0L, false);
			}

			@Override
			public OnSynced ifCommitSynced(VertexMetadata vertexMetadata) {
				return onSynced -> {
					onSynced.run();
					return (notSynced, opaque) -> { };
				};
			}

			@Override
			public void commit(Command command, VertexMetadata vertexMetadata) {
				// Nothing to do here
			}
		};
	}
}
