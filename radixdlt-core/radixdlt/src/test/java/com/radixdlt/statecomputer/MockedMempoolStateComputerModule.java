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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.ledger.MockPrepared;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.SimpleMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolRejectedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Simple Mempool state computer
 */
public class MockedMempoolStateComputerModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger();

	@Override
	protected void configure() {
		bind(new TypeLiteral<Mempool<?>>() { }).to(new TypeLiteral<Mempool<Txn>>() { }).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton
	private Mempool<Txn> mempool(
		MempoolConfig mempoolConfig,
		SystemCounters systemCounters,
		Random random
	) {
		return new SimpleMempool(mempoolConfig, systemCounters, random);
	}

	@Provides
	@Singleton
	private StateComputerLedger.StateComputer stateComputer(Mempool<Txn> mempool, SystemCounters counters) {
		return new StateComputerLedger.StateComputer() {
			@Override
			public void addToMempool(Txn txn, BFTNode origin) {
				try {
					mempool.add(txn);
					counters.set(SystemCounters.CounterType.MEMPOOL_COUNT, mempool.getCount());
				} catch (MempoolRejectedException e) {
					log.error(e);
				}
			}

			@Override
			public List<Txn> getNextTxnsFromMempool(List<StateComputerLedger.PreparedTxn> prepared) {
				return mempool.getTxns(1, List.of());
			}

			@Override
			public StateComputerLedger.StateComputerResult prepare(
				List<StateComputerLedger.PreparedTxn> previous,
				List<Txn> next,
				long epoch,
				View view,
				long timestamp
			) {
				return new StateComputerLedger.StateComputerResult(
					next.stream().map(MockPrepared::new).collect(Collectors.toList()),
					Map.of()
				);
			}

			@Override
			public void commit(VerifiedTxnsAndProof txnsAndProof, VerifiedVertexStoreState vertexStoreState) {
				mempool.committed(txnsAndProof.getTxns());
				counters.set(SystemCounters.CounterType.MEMPOOL_COUNT, mempool.getCount());
			}
		};
	}
}
