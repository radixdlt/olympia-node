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
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolMetadata;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
public final class RadixEngineMempool implements Mempool<REProcessedTxn> {
	private static final Logger logger = LogManager.getLogger();

	private final ConcurrentHashMap<AID, Pair<REProcessedTxn, MempoolMetadata>> data = new ConcurrentHashMap<>();
	private final Map<SubstateId, Set<AID>> substateIndex = new ConcurrentHashMap<>();
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final int maxSize;

	@Inject
	public RadixEngineMempool(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		@MempoolMaxSize int maxSize
	) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("mempool.maxSize must be positive: " + maxSize);
		}
		this.maxSize = maxSize;
		this.radixEngine = radixEngine;
	}

	@Override
	public void add(Txn txn) throws MempoolRejectedException {
		if (this.data.size() >= maxSize) {
			throw new MempoolFullException(
				String.format("Mempool full: %s of %s items", this.data.size(), maxSize)
			);
		}

		if (this.data.containsKey(txn.getId())) {
			throw new MempoolDuplicateException(String.format("Mempool already has command %s", txn.getId()));
		}

		final List<REProcessedTxn> radixEngineTxns;
		try {
			RadixEngine.RadixEngineBranch<LedgerAndBFTProof> checker = radixEngine.transientBranch();
			radixEngineTxns = checker.execute(List.of(txn)).getFirst();
		} catch (RadixEngineException e) {
			// TODO: allow missing dependency atoms to live for a certain amount of time
			throw new MempoolRejectedException(e);
		} finally {
			radixEngine.deleteBranches();
		}

		var mempoolTxn = MempoolMetadata.create(System.currentTimeMillis());
		var data = Pair.of(radixEngineTxns.get(0), mempoolTxn);
		this.data.put(txn.getId(), data);
		radixEngineTxns.get(0).substateDependencies()
			.forEach(substateId -> substateIndex.merge(substateId, Set.of(txn.getId()), Sets::union));
	}

	@Override
	public List<Txn> committed(List<REProcessedTxn> transactions) {
		final var removed = new ArrayList<Txn>();
		final var committedIds = transactions.stream()
			.map(p -> p.getTxn().getId())
			.collect(Collectors.toSet());

		transactions.stream()
			.flatMap(REProcessedTxn::stateUpdates)
			.filter(REStateUpdate::isShutDown)
			.forEach(instruction -> {
				var substateId = instruction.getId();
				Set<AID> txnIds = substateIndex.remove(substateId);
				if (txnIds == null) {
					return;
				}

				for (var txnId : txnIds) {
					var toRemove = data.remove(txnId);
					// TODO: Cleanup
					if (toRemove != null && !committedIds.contains(toRemove.getFirst().getTxn().getId())) {
						removed.add(toRemove.getFirst().getTxn());
					}
				}
			});

		if (!removed.isEmpty()) {
			logger.debug("Evicting {} txns from mempool", removed.size());
		}

		return removed;
	}

	@Override
	public List<Txn> getTxns(int count, List<REProcessedTxn> prepared) {
		// TODO: Order by highest fees paid
		var copy = new TreeSet<>(data.keySet());
		prepared.stream()
			.flatMap(REProcessedTxn::stateUpdates)
			.filter(REStateUpdate::isShutDown)
			.flatMap(i -> substateIndex.getOrDefault(i.getId(), Set.of()).stream())
			.distinct()
			.forEach(copy::remove);

		var txns = new ArrayList<Txn>();

		for (int i = 0; i < count && !copy.isEmpty(); i++) {
			var txId = copy.first();
			copy.remove(txId);
			var txnData = data.get(txId);
			txnData.getFirst().stateUpdates()
				.filter(REStateUpdate::isShutDown)
				.flatMap(inst -> substateIndex.getOrDefault(inst.getId(), Set.of()).stream())
				.distinct()
				.forEach(copy::remove);

			txns.add(txnData.getFirst().getTxn());
		}

		return txns;
	}

	public Set<SubstateId> getShuttingDownSubstates() {
		return new HashSet<>(substateIndex.keySet());
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

	public int getCount() {
		return this.data.size();
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
			getClass().getSimpleName(), System.identityHashCode(this), this.data.size(), maxSize);
	}
}
