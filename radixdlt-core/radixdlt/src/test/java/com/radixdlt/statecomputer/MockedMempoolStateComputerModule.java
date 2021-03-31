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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.ledger.MockPrepared;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.SimpleMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolRejectedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Random;

/**
 * Simple Mempool state computer
 */
public class MockedMempoolStateComputerModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger();

	@Provides
	@Singleton
	private Mempool<Txn> mempool(
		@MempoolMaxSize int maxSize,
		SystemCounters systemCounters,
		Random random
	) {
		return new SimpleMempool(maxSize, systemCounters, random);
	}

	@Provides
	@Singleton
	private StateComputerLedger.StateComputer stateComputer(Mempool<Txn> mempool) {
		return new StateComputerLedger.StateComputer() {
			@Override
			public void addToMempool(Txn txn, BFTNode origin) {
				try {
					mempool.add(txn);
				} catch (MempoolRejectedException e) {
					log.error(e);
				}
			}

			@Override
			public Command getNextCommandFromMempool(ImmutableList<StateComputerLedger.PreparedTxn> prepared) {
				final List<Txn> txns = mempool.getTxns(1, List.of());
				return !txns.isEmpty() ? new Command(txns.get(0).getPayload()) : null;
			}

			@Override
			public StateComputerLedger.StateComputerResult prepare(
				List<StateComputerLedger.PreparedTxn> previous,
				Command next,
				long epoch,
				View view,
				long timestamp
			) {
				return new StateComputerLedger.StateComputerResult(
					next == null
						? ImmutableList.of()
						: ImmutableList.of(new MockPrepared(Txn.create(next.getPayload()))),
					ImmutableMap.of()
				);
			}

			@Override
			public void commit(VerifiedTxnsAndProof txnsAndProof, VerifiedVertexStoreState vertexStoreState) {
				mempool.committed(txnsAndProof.getTxns());
			}
		};
	}
}
