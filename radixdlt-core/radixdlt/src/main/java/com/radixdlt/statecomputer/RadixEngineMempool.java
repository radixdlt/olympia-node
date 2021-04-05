/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.statecomputer;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.RETxn;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolMetadata;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A mempool which uses internal radix engine to be more efficient.
 */
@Singleton
public final class RadixEngineMempool implements Mempool<RETxn> {
	private final ConcurrentHashMap<AID, Pair<RETxn, MempoolMetadata>> data = new ConcurrentHashMap<>();
	private final Map<SubstateId, Set<AID>> substateIndex = new HashMap<>();
	private final MempoolConfig mempoolConfig;
	private final SystemCounters counters;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	public RadixEngineMempool(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		MempoolConfig mempoolConfig,
		SystemCounters counters
	) {
		if (mempoolConfig.maxSize() <= 0) {
			throw new IllegalArgumentException("mempool.maxSize must be positive: " + mempoolConfig.maxSize());
		}
		this.radixEngine = radixEngine;
		this.mempoolConfig = Objects.requireNonNull(mempoolConfig);
		this.counters = Objects.requireNonNull(counters);
	}

	@Override
	public void add(Txn txn) throws MempoolRejectedException {
		if (this.data.size() >= this.mempoolConfig.maxSize()) {
			throw new MempoolFullException(
				String.format("Mempool full: %s of %s items", this.data.size(), this.mempoolConfig.maxSize())
			);
		}

		if (this.data.containsKey(txn.getId())) {
			throw new MempoolDuplicateException(String.format("Mempool already has command %s", txn.getId()));
		}

		final List<RETxn> radixEngineTxns;
		try {
			RadixEngine.RadixEngineBranch<LedgerAndBFTProof> checker = radixEngine.transientBranch();
			radixEngineTxns = checker.execute(List.of(txn));
		} catch (RadixEngineException e) {
			// TODO: allow missing dependency atoms to live for a certain amount of time
			throw new RadixEngineMempoolException(e);
		} finally {
			radixEngine.deleteBranches();
		}

		var mempoolTxn = MempoolMetadata.create(System.currentTimeMillis());
		var data = Pair.of(radixEngineTxns.get(0), mempoolTxn);
		this.data.put(txn.getId(), data);
		for (var instruction : radixEngineTxns.get(0).instructions()) {
			if (instruction.getSpin() == Spin.DOWN) {
				var substateId = instruction.getSubstate().getId();
				substateIndex.merge(substateId, Set.of(txn.getId()), Sets::union);
			}
		}

		updateCounts();
	}

	@Override
	public List<Pair<Txn, Exception>> committed(List<RETxn> transactions) {
		final var removed = new ArrayList<Pair<Txn, Exception>>();
		final var committedIds = transactions.stream()
			.map(p -> p.getTxn().getId())
			.collect(Collectors.toSet());

		transactions.stream()
			.flatMap(t -> t.instructions().stream())
			.filter(i -> i.getSpin() == Spin.DOWN)
			.forEach(instruction -> {
				var substateId = instruction.getSubstate().getId();
				Set<AID> txnIds = substateIndex.remove(substateId);
				if (txnIds == null) {
					return;
				}

				for (var txnId : txnIds) {
					var toRemove = data.remove(txnId);
					// TODO: Cleanup
					if (toRemove != null && !committedIds.contains(toRemove.getFirst().getTxn().getId())) {
						removed.add(Pair.of(toRemove.getFirst().getTxn(),
							new RadixEngineMempoolException(
								new RadixEngineException(
									toRemove.getFirst().getTxn(),
									toRemove.getFirst().instructions(),
									RadixEngineErrorCode.CM_ERROR,
									"Mempool evicted"
								)
							)
						));
					}
				}
			});

		updateCounts();
		return removed;
	}

	@Override
	public List<Txn> getTxns(int count, List<RETxn> prepared) {
		// TODO: Order by highest fees paid
		var copy = new TreeSet<>(data.keySet());
		prepared.stream()
			.flatMap(t -> t.instructions().stream())
			.filter(i -> i.getSpin() == Spin.DOWN)
			.flatMap(i -> substateIndex.getOrDefault(i.getSubstate().getId(), Set.of()).stream())
			.distinct()
			.forEach(copy::remove);

		var txns = new ArrayList<Txn>();

		for (int i = 0; i < count && !copy.isEmpty(); i++) {
			var txId = copy.first();
			copy.remove(txId);
			var txnData = data.get(txId);
			txnData.getFirst().instructions().stream().filter(inst -> inst.getSpin() == Spin.DOWN)
				.flatMap(inst -> substateIndex.getOrDefault(inst.getSubstate().getId(), Set.of()).stream())
				.distinct()
				.forEach(copy::remove);

			txns.add(txnData.getFirst().getTxn());
		}

		return txns;
	}

	@Override
	public List<Txn> scanUpdateAndGet(Predicate<MempoolMetadata> predicate, Consumer<MempoolMetadata> operator) {
		return this.data
			.values().stream()
			.filter(e -> predicate.test(e.getSecond()))
			.peek(e -> operator.accept(e.getSecond()))
			.map(e -> e.getFirst().getTxn())
			.collect(Collectors.toList());
	}

	private void updateCounts() {
		this.counters.set(CounterType.MEMPOOL_COUNT, this.data.size());
		this.counters.set(CounterType.MEMPOOL_MAXCOUNT, this.mempoolConfig.maxSize());
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
			getClass().getSimpleName(), System.identityHashCode(this), this.data.size(), this.mempoolConfig.maxSize());
	}
}
