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

package com.radixdlt.ledger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Synchronizes execution
 */
public final class StateComputerLedger implements Ledger, NextCommandGenerator {
	private static final Logger log = LogManager.getLogger();

	public interface StateComputer {
		boolean prepare(VerifiedVertex vertex);
		Optional<BFTValidatorSet> commit(VerifiedCommandsAndProof verifiedCommandsAndProof);
	}

	public interface CommittedSender {
		// TODO: batch these
		void sendCommitted(VerifiedCommandsAndProof committedCommand, BFTValidatorSet validatorSet);
	}

	public interface CommittedStateSyncSender {
		void sendCommittedStateSync(VerifiedLedgerHeaderAndProof header, Object opaque);
	}

	private final Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private final Mempool mempool;
	private final StateComputer stateComputer;
	private final CommittedStateSyncSender committedStateSyncSender;
	private final CommittedSender committedSender;
	private final SystemCounters counters;
	private final LedgerAccumulator accumulator;

	private final Object lock = new Object();
	private VerifiedLedgerHeaderAndProof currentLedgerHeader;
	private final TreeMap<Long, Set<Object>> committedStateSyncers = new TreeMap<>();

	@Inject
	public StateComputerLedger(
		Comparator<VerifiedLedgerHeaderAndProof> headerComparator,
		VerifiedLedgerHeaderAndProof initialLedgerState,
		Mempool mempool,
		StateComputer stateComputer,
		CommittedStateSyncSender committedStateSyncSender,
		CommittedSender committedSender,
		LedgerAccumulator accumulator,
		SystemCounters counters
	) {
		this.headerComparator = Objects.requireNonNull(headerComparator);
		this.currentLedgerHeader = initialLedgerState;
		this.mempool = Objects.requireNonNull(mempool);
		this.stateComputer = Objects.requireNonNull(stateComputer);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
		this.committedSender = Objects.requireNonNull(committedSender);
		this.counters = Objects.requireNonNull(counters);
		this.accumulator = Objects.requireNonNull(accumulator);
	}

	@Override
	public Command generateNextCommand(View view, Set<Hash> prepared) {
		final List<Command> commands = mempool.getCommands(1, prepared);
		return !commands.isEmpty() ? commands.get(0) : null;
	}

	@Override
	public LedgerHeader prepare(VerifiedVertex vertex) {
		final LedgerHeader parent = vertex.getParentHeader().getLedgerHeader();

		boolean isEndOfEpoch = stateComputer.prepare(vertex);

		final AccumulatorState accumulatorState;
		if (parent.isEndOfEpoch() || vertex.getCommand() == null) {
			// Don't execute atom if in process of epoch change
			accumulatorState = parent.getAccumulatorState();
		} else {
			accumulatorState = this.accumulator.accumulate(parent.getAccumulatorState(), vertex.getCommand());
		}

		final long timestamp = vertex.getQC().getTimestampedSignatures().weightedTimestamp();
		return LedgerHeader.create(
			parent.getEpoch(),
			vertex.getView(),
			accumulatorState,
			timestamp,
			isEndOfEpoch
		);
	}

	@Override
	public OnSynced ifCommitSynced(VerifiedLedgerHeaderAndProof committedLedgerState) {
		synchronized (lock) {
			if (committedLedgerState.getStateVersion() <= this.currentLedgerHeader.getStateVersion()) {
				if (headerComparator.compare(committedLedgerState, this.currentLedgerHeader) > 0) {
					// Can happen on epoch changes
					// TODO: Need to cleanup this logic, can't skip epochs
					this.commit(new VerifiedCommandsAndProof(ImmutableList.of(), committedLedgerState));
				}
				return onSync -> {
					onSync.run();
					return (onNotSynced, opaque) -> { };
				};
			} else {
				return onSync -> (onNotSynced, opaque) -> {
					this.committedStateSyncers.merge(committedLedgerState.getStateVersion(), Collections.singleton(opaque), Sets::union);
					onNotSynced.run();
				};
			}
		}
	}

	@Override
	public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof) {
		this.counters.increment(CounterType.LEDGER_PROCESSED);
		synchronized (lock) {
			final VerifiedLedgerHeaderAndProof committedHeader = verifiedCommandsAndProof.getHeader();
			if (headerComparator.compare(committedHeader, this.currentLedgerHeader) <= 0) {
				return;
			}

			// Callers of commit() should be aware of currentLedgerHeader.getStateVersion()
			// and only call commit with a first version <= currentVersion + 1
			if (currentLedgerHeader.getStateVersion() + 1 < verifiedCommandsAndProof.getFirstVersion()) {
				throw new IllegalStateException("Trying to commit version " + verifiedCommandsAndProof.getFirstVersion()
					+ " but current header is " + currentLedgerHeader);
			}

			// Remove commands which have already been committed
			VerifiedCommandsAndProof commandsToStore = verifiedCommandsAndProof
				.truncateFromVersion(this.currentLedgerHeader.getStateVersion());

			// persist
			Optional<BFTValidatorSet> validatorSet = this.stateComputer.commit(commandsToStore);

			// TODO: move all of the following to post-persist event handling
			this.currentLedgerHeader = committedHeader;
			this.counters.set(CounterType.LEDGER_STATE_VERSION, this.currentLedgerHeader.getStateVersion());

			commandsToStore.forEach((v, cmd) -> this.mempool.removeCommitted(cmd.getHash()));
			committedSender.sendCommitted(commandsToStore, validatorSet.orElse(null));

			// TODO: Verify headers match
			Collection<Set<Object>> listeners = this.committedStateSyncers.headMap(this.currentLedgerHeader.getStateVersion(), true)
				.values();
			Iterator<Set<Object>> listenersIterator = listeners.iterator();
			while (listenersIterator.hasNext()) {
				Set<Object> opaqueObjects = listenersIterator.next();
				for (Object opaque : opaqueObjects) {
					committedStateSyncSender.sendCommittedStateSync(this.currentLedgerHeader, opaque);
				}
				listenersIterator.remove();
			}
		}
	}
}
