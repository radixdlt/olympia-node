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

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.BaseLedgerUpdate;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import java.util.Random;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Ledger;

/**
 * Module that appears to be synced at random.
 */
public class DeterministicRandomlySyncedLedgerModule extends AbstractModule {
	private final Random random;

	public DeterministicRandomlySyncedLedgerModule(Random random) {
		this.random = random;
	}

	@Override
	public void configure() {
		bind(NextCommandGenerator.class).toInstance((view, aids) -> null);
		bind(SyncLedgerRequestSender.class).toInstance(req -> { });
		Multibinder.newSetBinder(binder(), LedgerUpdateSender.class);
	}

	@Provides
	@Singleton
	Ledger syncedExecutor(LedgerUpdateSender ledgerUpdateSender) {
		return new Ledger() {
			@Override
			public LedgerHeader prepare(VerifiedVertex vertex) {
				return LedgerHeader.create(
					vertex.getParentHeader().getLedgerHeader().getEpoch(),
					vertex.getView(),
					new AccumulatorState(0, Hash.ZERO_HASH),
					0L,
					false
				);
			}

			@Override
			public OnSynced ifCommitSynced(VerifiedLedgerHeaderAndProof header) {
				return onSynced -> {
					boolean synced = random.nextBoolean();
					if (synced) {
						onSynced.run();
					}

					return notSynced -> {
						if (!synced) {
							notSynced.run();

							// TODO: Fix this
							VerifiedCommandsAndProof commandsAndProof = new VerifiedCommandsAndProof(
								ImmutableList.of(),
								header
							);
							BaseLedgerUpdate baseLedgerUpdate = new BaseLedgerUpdate(commandsAndProof, null);
							ledgerUpdateSender.sendLedgerUpdate(baseLedgerUpdate);
						}
					};
				};
			}

			@Override
			public void commit(VerifiedCommandsAndProof command) {
				// Nothing to do here
			}
		};
	}
}
