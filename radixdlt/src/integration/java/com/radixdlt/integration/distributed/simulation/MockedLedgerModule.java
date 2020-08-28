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

import java.util.Objects;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.consensus.PreparedCommand;
import io.reactivex.rxjava3.core.Observable;

public class MockedLedgerModule extends AbstractModule {
	private final BFTValidatorSet validatorSet;

	public MockedLedgerModule(BFTValidatorSet validatorSet) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
	}

	@Override
	public void configure() {
		bind(CommittedStateSyncRx.class).toInstance(Observable::never);
		bind(EpochChangeRx.class).toInstance(Observable::never);
		PreparedCommand preparedCommand = PreparedCommand.create(0, 0L, true);
		EpochChange initialEpoch = new EpochChange(VertexMetadata.ofGenesisAncestor(preparedCommand), validatorSet);
		bind(EpochChange.class).toInstance(initialEpoch);
		bind(NextCommandGenerator.class).toInstance((view, aids) -> null);
		bind(SyncRequestSender.class).toInstance(req -> { });
	}

	@Provides
	@Singleton
	Ledger syncedLedger() {
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
