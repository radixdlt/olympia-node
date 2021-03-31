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
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.ParsedTransaction;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.atom.Atom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A mempool which uses internal radix engine to be more efficient.
 */
public final class RadixEngineMempool implements Mempool<ParsedTransaction> {
	private final ConcurrentHashMap<Txn, Atom> data = new ConcurrentHashMap<>();
	private final Map<SubstateId, Set<Txn>> particleIndex = new HashMap<>();
	private final int maxSize;
	private final SystemCounters counters;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	public RadixEngineMempool(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		@MempoolMaxSize int maxSize,
		SystemCounters counters
	) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("mempool.maxSize must be positive: " + maxSize);
		}
		this.radixEngine = radixEngine;
		this.maxSize = maxSize;
		this.counters = Objects.requireNonNull(counters);
	}

	@Override
	public void add(Txn txn) throws MempoolRejectedException {
		if (this.data.size() >= this.maxSize) {
			throw new MempoolFullException(
				String.format("Mempool full: %s of %s items", this.data.size(), this.maxSize)
			);
		}

		if (this.data.containsKey(txn)) {
			throw new MempoolDuplicateException(String.format("Mempool already has command %s", txn.getId()));
		}

		final List<ParsedTransaction> parsedTransactions;
		try {
			RadixEngine.RadixEngineBranch<LedgerAndBFTProof> checker = radixEngine.transientBranch();
			parsedTransactions = checker.execute(List.of(txn));
		} catch (RadixEngineException e) {
			// TODO: allow missing dependency atoms to live for a certain amount of time
			throw new RadixEngineMempoolException(e);
		} finally {
			radixEngine.deleteBranches();
		}

		this.data.put(txn, parsedTransactions.get(0).getAtom());

		for (var instruction : parsedTransactions.get(0).instructions()) {
			if (instruction.getSpin() == Spin.DOWN) {
				var substateId = instruction.getSubstate().getId();
				particleIndex.merge(substateId, Set.of(txn), Sets::union);
			}
		}

		updateCounts();
	}

	// Hack, remove later
	private static AID atomIdOf(Atom atom) {
		var dson = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
		var firstHash = HashUtils.sha256(dson);
		var secondHash = HashUtils.sha256(firstHash.asBytes());
		return AID.from(secondHash.asBytes());
	}

	@Override
	public List<Pair<Txn, Exception>> committed(List<ParsedTransaction> transactions) {
		final var removed = new ArrayList<Pair<Txn, Exception>>();
		final var atomIds = transactions.stream()
			.map(p -> p.getTxn().getId())
			.collect(Collectors.toSet());

		transactions.stream()
			.flatMap(t -> t.instructions().stream())
			.filter(i -> i.getSpin() == Spin.DOWN)
			.forEach(instruction -> {
				var substateId = instruction.getSubstate().getId();
				Set<Txn> txns = particleIndex.remove(substateId);
				if (txns == null) {
					return;
				}

				for (var txn : txns) {
					var toRemove = data.remove(txn);
					// TODO: Cleanup
					if (toRemove != null && !atomIds.contains(atomIdOf(toRemove))) {
						removed.add(Pair.of(txn, new RadixEngineMempoolException(
							new RadixEngineException(
								txn,
								RadixEngineErrorCode.CM_ERROR,
								"Mempool evicted",
								DataPointer.ofAtom()
							)
						)));
					}
				}
			});

		updateCounts();
		return removed;
	}

	// TODO: Order by highest fees paid
	@Override
	public List<Txn> getTxns(int count, List<ParsedTransaction> prepared) {
		var copy = new HashSet<>(data.keySet());
		prepared.stream()
			.flatMap(t -> t.instructions().stream())
			.filter(i -> i.getSpin() == Spin.DOWN)
			.flatMap(i -> particleIndex.getOrDefault(i.getSubstate().getId(), Set.of()).stream())
			.distinct()
			.forEach(copy::remove);

		return copy.stream().limit(count).collect(Collectors.toList());
	}

	private void updateCounts() {
		this.counters.set(SystemCounters.CounterType.MEMPOOL_COUNT, this.data.size());
		this.counters.set(SystemCounters.CounterType.MEMPOOL_MAXCOUNT, this.maxSize);
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
			getClass().getSimpleName(), System.identityHashCode(this), this.data.size(), this.maxSize);
	}
}
