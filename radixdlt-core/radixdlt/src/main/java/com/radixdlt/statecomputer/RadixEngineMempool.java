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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.utils.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * A mempool which uses internal radix engine to be more efficient.
 */
public final class RadixEngineMempool implements Mempool<ClientAtom, AID> {
	private final HashMap<AID, ClientAtom> data = new HashMap<>();
	private final Map<Particle, Set<AID>> particleIndex = new HashMap<>();
	private final int maxSize;
	private final SystemCounters counters;
	private final Random random;
	private final RadixEngine<LedgerAtom> radixEngine;

	@Inject
	public RadixEngineMempool(
		RadixEngine<LedgerAtom> radixEngine,
		@MempoolMaxSize int maxSize,
		SystemCounters counters,
		Random random
	) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("mempool.maxSize must be positive: " + maxSize);
		}
		this.radixEngine = radixEngine;
		this.maxSize = maxSize;
		this.counters = Objects.requireNonNull(counters);
		this.random = Objects.requireNonNull(random);
	}

	@Override
	public void add(ClientAtom atom) throws MempoolRejectedException {
		if (this.data.size() >= this.maxSize) {
			throw new MempoolFullException(
				String.format("Mempool full: %s of %s items", this.data.size(), this.maxSize)
			);
		}

		try {
			RadixEngine.RadixEngineBranch<LedgerAtom> checker = radixEngine.transientBranch();
			checker.checkAndStore(atom);
		} catch (RadixEngineException e) {
			// TODO: allow missing dependency atoms to live for a certain amount of time
			throw new RadixEngineMempoolException(e);
		} finally {
			radixEngine.deleteBranches();
		}

		atom.getCMInstruction().getMicroInstructions().stream()
			.filter(CMMicroInstruction::isPush)
			.map(CMMicroInstruction::getParticle)
			.distinct()
			.forEach(p -> particleIndex.merge(p, Set.of(atom.getAID()), Sets::union));


		if (null != this.data.put(atom.getAID(), atom)) {
			throw new MempoolDuplicateException(String.format("Mempool already has command %s", atom.getAID()));
		}

		updateCounts();
	}

	private void removeAtom(ClientAtom atom) {
		atom.getCMInstruction().getMicroInstructions().stream()
			.filter(CMMicroInstruction::isPush)
			.map(CMMicroInstruction::getParticle)
			.distinct()
			.forEach(p -> particleIndex.computeIfPresent(p, (k, aids) -> {
				Set<AID> s = Sets.filter(aids, a -> !a.equals(atom.getAID()));
				return s.isEmpty() ? null : s;
			}));
		data.remove(atom.getAID());
	}

	@Override
	public List<Pair<ClientAtom, Exception>> committed(List<ClientAtom> commands) {
		commands.forEach(this::removeAtom);
		final List<Pair<ClientAtom, Exception>> removed = new ArrayList<>();
		commands.stream()
			.map(ClientAtom::getCMInstruction)
			.flatMap(i -> i.getMicroInstructions().stream())
			.filter(CMMicroInstruction::isPush)
			.map(CMMicroInstruction::getParticle)
			.distinct()
			.flatMap(p -> particleIndex.getOrDefault(p, Set.of()).stream())
			.distinct()
			.map(data::get)
			.forEach(clientAtom -> {
				try {
					RadixEngine.RadixEngineBranch<LedgerAtom> checker = radixEngine.transientBranch();
					checker.checkAndStore(clientAtom);
				} catch (RadixEngineException e) {
					removed.add(Pair.of(clientAtom, new RadixEngineMempoolException(e)));
					removeAtom(clientAtom);
				} finally {
					radixEngine.deleteBranches();
				}
			});

		updateCounts();
		return removed;
	}

	// TODO: Order by highest fees paid
	@Override
	public List<ClientAtom> getCommands(int count, Set<AID> seen) {
		int size = Math.min(count, this.data.size());
		if (size > 0) {
			List<ClientAtom> commands = Lists.newArrayList();
			var values = new ArrayList<>(this.data.values());
			Collections.shuffle(values, random);

			Iterator<ClientAtom> i = values.iterator();
			while (commands.size() < size && i.hasNext()) {
				ClientAtom a = i.next();
				if (!seen.contains(a.getAID())) {
					commands.add(a);
				}
			}
			return commands;
		} else {
			return Collections.emptyList();
		}
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
