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
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof.OrderByEpochAndVersionComparator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.ledger.StateComputerLedger.CommittedStateSyncSender;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import java.util.Comparator;
import java.util.Set;

/**
 * Module which manages ledger state and synchronization of updates to ledger state
 */
public class LedgerModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(EpochManager.class).in(Scopes.SINGLETON);
		bind(Ledger.class).to(StateComputerLedger.class).in(Scopes.SINGLETON);
		// These multibindings are part of our dependency graph, so create the modules here
		Multibinder.newSetBinder(binder(), CommittedSender.class);
		bind(new TypeLiteral<Comparator<VerifiedLedgerHeaderAndProof>>() { }).to(OrderByEpochAndVersionComparator.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton
	private StateComputerLedger ledger(
		Comparator<VerifiedLedgerHeaderAndProof> headerComparator,
		VerifiedLedgerHeaderAndProof genesisLedgerState,
		Mempool mempool,
		StateComputer stateComputer,
		CommittedStateSyncSender committedStateSyncSender,
		Set<CommittedSender> committedSenders,
		SystemCounters counters
	) {
		CommittedSender committedSender = (committed, vset) -> committedSenders.forEach(s -> s.sendCommitted(committed, vset));

		return new StateComputerLedger(
			headerComparator,
			genesisLedgerState,
			mempool,
			stateComputer,
			committedStateSyncSender,
			committedSender,
			counters
		);
	}

	// TODO: Load from storage
	@Provides
	private EpochChange initialEpoch(VerifiedLedgerHeaderAndProof ancestor, BFTValidatorSet validatorSet) {
		return new EpochChange(ancestor, validatorSet);
	}
}
