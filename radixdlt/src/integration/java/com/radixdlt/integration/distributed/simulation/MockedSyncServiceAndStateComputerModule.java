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
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.syncer.StateComputerExecutedCommands;
import com.radixdlt.syncer.SyncExecutor.StateComputer;
import com.radixdlt.syncer.SyncExecutor.StateComputerExecutedCommand;
import com.radixdlt.syncer.SyncExecutor.SyncService;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MockedSyncServiceAndStateComputerModule extends AbstractModule {
	private final Function<Long, BFTValidatorSet> validatorSetMapping;
	private final View epochHighView;
	private final ConcurrentHashMap<Long, CommittedAtom> sharedCommittedAtoms;

	public MockedSyncServiceAndStateComputerModule(
		ConcurrentHashMap<Long, CommittedAtom> sharedCommittedAtoms,
		View epochHighView,
		Function<Long, BFTValidatorSet> validatorSetMapping
	) {
		this.sharedCommittedAtoms = sharedCommittedAtoms;
		this.validatorSetMapping = validatorSetMapping;
		this.epochHighView = epochHighView;
	}

	@Override
	public void configure() {
		bind(Mempool.class).to(EmptyMempool.class);
	}

	@Provides
	@Singleton
	private VertexMetadata genesisMetadata() {
		return VertexMetadata.ofGenesisAncestor(validatorSetMapping.apply(1L));
	}

	@Provides
	@Singleton
	private StateComputer stateComputer() {
		return new StateComputer() {
			@Override
			public Optional<BFTValidatorSet> execute(Vertex vertex) {
				if (vertex.getView().compareTo(epochHighView) >= 0) {
					return Optional.of(validatorSetMapping.apply(vertex.getEpoch() + 1));
				} else {
					return Optional.empty();
				}
			}

			@Override
			public StateComputerExecutedCommand commit(CommittedAtom committedAtom) {
				sharedCommittedAtoms.put(committedAtom.getVertexMetadata().getStateVersion(), committedAtom);
				return StateComputerExecutedCommands.success(committedAtom, null);
			}
		};
	}

	// TODO: change this to a service
	@ProvidesIntoSet
	SyncService syncService(
		SyncedExecutor<CommittedAtom> syncedExecutor
	) {
		return request -> {
			final long targetVersion = request.getTarget().getStateVersion();
			final long initVersion = request.getCurrentVersion() + 1;
			for (long version = initVersion; version <= targetVersion; version++) {
				CommittedAtom atom = sharedCommittedAtoms.get(version);
				syncedExecutor.commit(atom);
			}
		};
	}
}
