package com.radixdlt.statecomputer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class RadixEngineMempool implements Mempool<ClientAtom, AID> {
	private final LinkedHashMap<AID, ClientAtom> data = Maps.newLinkedHashMap();

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
			if (e.getErrorCode() != RadixEngineErrorCode.MISSING_DEPENDENCY) {
				throw new MempoolRejectedException(e.getMessage());
			}
		} finally {
			radixEngine.deleteBranches();
		}

		if (null != this.data.put(atom.getAID(), atom)) {
			throw new MempoolDuplicateException(String.format("Mempool already has command %s", atom.getAID()));
		}

		updateCounts();
	}

	@Override
	public List<Pair<ClientAtom, Exception>> committed(List<ClientAtom> commands) {
		commands.forEach(cmd -> this.data.remove(cmd.getAID()));
		final List<Pair<ClientAtom, Exception>> removed = new ArrayList<>();
		for (ClientAtom clientAtom : data.values()) {
			try {
				RadixEngine.RadixEngineBranch<LedgerAtom> checker = radixEngine.transientBranch();
				checker.checkAndStore(clientAtom);
			} catch (RadixEngineException e) {
				if (e.getErrorCode() != RadixEngineErrorCode.MISSING_DEPENDENCY) {
					removed.add(Pair.of(clientAtom, e));
				}
			} finally {
				radixEngine.deleteBranches();
			}
		}
		removed.stream()
			.map(Pair::getFirst)
			.map(ClientAtom::getAID)
			.forEach(this.data::remove);
		updateCounts();
		return removed;
	}

	@Override
	public void remove(ClientAtom toRemove) {
		this.data.remove(toRemove.getAID());
		updateCounts();
	}

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

	@Override
	public int count() {
		return this.data.size();
	}

	private void updateCounts() {
		this.counters.set(SystemCounters.CounterType.MEMPOOL_COUNT, count());
		this.counters.set(SystemCounters.CounterType.MEMPOOL_MAXCOUNT, this.maxSize);
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
				getClass().getSimpleName(), System.identityHashCode(this), count(), this.maxSize);
	}
}
