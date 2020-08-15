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
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.SyncExecutor;
import com.radixdlt.syncer.SyncExecutor.CommittedSender;
import com.radixdlt.syncer.SyncExecutor.CommittedStateSyncSender;
import com.radixdlt.syncer.SyncExecutor.StateComputer;
import com.radixdlt.syncer.SyncExecutor.SyncService;

/**
 * Module which manages synchronized execution
 */
public class SyncExecutionModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(new TypeLiteral<SyncedExecutor<CommittedAtom>>() { }).to(SyncExecutor.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton
	private SyncExecutor syncedEpochExecutor(
		Mempool mempool,
		StateComputer stateComputer,
		CommittedStateSyncSender committedStateSyncSender,
		CommittedSender committedSender,
		EpochChangeSender epochChangeSender,
		SyncService syncService,
		SystemCounters counters
	) {
		return new SyncExecutor(
			0L,
			mempool,
			stateComputer,
			committedStateSyncSender,
			committedSender,
			epochChangeSender,
			syncService,
			counters
		);
	}

	// TODO: Load from storage
	@Provides
	@Singleton
	private EpochChange initialEpoch(StateComputer stateComputer) {
		VertexMetadata ancestor = VertexMetadata.ofGenesisAncestor();
		return new EpochChange(
			ancestor, stateComputer.getValidatorSet(ancestor.getEpoch() + 1)
		);
	}
}
