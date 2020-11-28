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
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof.OrderByEpochAndVersionComparator;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.Pair;
import java.util.Comparator;

/**
 * Module which manages ledger state and synchronization of updates to ledger state
 */
public class LedgerModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(Ledger.class).to(StateComputerLedger.class);
		bind(new TypeLiteral<Comparator<VerifiedLedgerHeaderAndProof>>() { }).to(OrderByEpochAndVersionComparator.class).in(Scopes.SINGLETON);
		bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
		bind(LedgerAccumulatorVerifier.class).to(SimpleLedgerAccumulatorAndVerifier.class);
		bind(StateComputerLedger.class).in(Scopes.SINGLETON);
	}

	@Provides
	private Comparator<AccumulatorState> accumulatorStateComparator() {
		return Comparator.comparingLong(AccumulatorState::getStateVersion);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<BFTCommittedUpdate> bftToLedgerCommittor(StateComputerLedger stateComputerLedger) {
		return stateComputerLedger.bftCommittedUpdateEventProcessor();
	}

	// TODO: This is a temporary fix until Mempool is fixed
	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<BFTCommittedUpdate> mempoolCommittor(Mempool mempool, Hasher hasher) {
		return e ->
			e.getCommitted().stream()
				.flatMap(PreparedVertex::errorCommands)
				.map(Pair::getFirst)
				.map(hasher::hash)
				.forEach(mempool::removeRejected);
	}
}
