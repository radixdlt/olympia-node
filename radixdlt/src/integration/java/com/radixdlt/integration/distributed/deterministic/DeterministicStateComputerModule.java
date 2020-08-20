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

package com.radixdlt.integration.distributed.deterministic;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.deterministic.network.DeterministicNetwork.DeterministicSender;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.SyncExecutor.StateComputer;
import com.radixdlt.syncer.SyncExecutor.CommittedStateSyncSender;
import java.util.Optional;
import java.util.function.LongFunction;

public class DeterministicStateComputerModule extends AbstractModule {
	private final LongFunction<BFTValidatorSet> validatorSetMapping;
	private final View epochHighView;

	public DeterministicStateComputerModule(
		View epochHighView,
		LongFunction<BFTValidatorSet> validatorSetMapping
	) {
		this.validatorSetMapping = validatorSetMapping;
		this.epochHighView = epochHighView;
	}

	@Override
	public void configure() {
		bind(Mempool.class).to(EmptyMempool.class);

		bind(EpochChangeSender.class).to(DeterministicSender.class);
		bind(CommittedStateSyncSender.class).to(DeterministicSender.class);
	}

	@Provides
	@Singleton
	private StateComputer stateComputer() {
		return new StateComputer() {
			@Override
			public Optional<BFTValidatorSet> prepare(Vertex vertex) {
				if (vertex.getView().compareTo(epochHighView) >= 0) {
					return Optional.of(validatorSetMapping.apply(vertex.getEpoch() + 1));
				} else {
					return Optional.empty();
				}
			}

			@Override
			public void commit(Command command, VertexMetadata vertexMetadata) {
				// Nothing to do here
			}
		};
	}
}
