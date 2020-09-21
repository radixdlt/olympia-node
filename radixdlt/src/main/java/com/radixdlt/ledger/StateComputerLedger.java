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

	public interface LedgerUpdateSender {
		void sendLedgerUpdate(LedgerUpdate ledgerUpdate);
	}

	public interface CommittedStateSyncSender {
		void sendCommittedStateSync(LedgerHeader header);
	}

	private final Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private final Mempool mempool;
	private final StateComputer stateComputer;
	private final CommittedStateSyncSender committedStateSyncSender;
	private final LedgerUpdateSender ledgerUpdateSender;
	private final SystemCounters counters;
	private final LedgerAccumulator accumulator;
	private final LedgerAccumulatorVerifier verifier;

	private final Object lock = new Object();
	private VerifiedLedgerHeaderAndProof currentLedgerHeader;
	private final TreeMap<Long, LedgerHeader> committedStateSyncers = new TreeMap<>();

	@Inject
	public StateComputerLedger(
		Comparator<VerifiedLedgerHeaderAndProof> headerComparator,
		VerifiedLedgerHeaderAndProof initialLedgerState,
		Mempool mempool,
		StateComputer stateComputer,
		CommittedStateSyncSender committedStateSyncSender,
		LedgerUpdateSender ledgerUpdateSender,
		LedgerAccumulator accumulator,
		LedgerAccumulatorVerifier verifier,
		SystemCounters counters
	) {
		this.headerComparator = Objects.requireNonNull(headerComparator);
		this.currentLedgerHeader = initialLedgerState;
		this.mempool = Objects.requireNonNull(mempool);
		this.stateComputer = Objects.requireNonNull(stateComputer);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
		this.ledgerUpdateSender = Objects.requireNonNull(ledgerUpdateSender);
		this.counters = Objects.requireNonNull(counters);
		this.accumulator = Objects.requireNonNull(accumulator);
		this.verifier = Objects.requireNonNull(verifier);
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
	public OnSynced ifCommitSynced(VerifiedLedgerHeaderAndProof committedLedgerHeader) {
		synchronized (lock) {
			if (this.currentLedgerHeader.getEpoch() != committedLedgerHeader.getEpoch() && !this.currentLedgerHeader.isEndOfEpoch()) {
				throw new IllegalStateException();
			}

			if (committedLedgerHeader.getStateVersion() <= this.currentLedgerHeader.getStateVersion()) {
				return onSync -> {
					onSync.run();
					return onNotSynced -> { };
				};
			} else {
				return onSync -> onNotSynced -> {
					// TODO: check if one already exists and doesn't match then its proof of byzantine failure
					this.committedStateSyncers.put(committedLedgerHeader.getStateVersion(), committedLedgerHeader.getRaw());
					onNotSynced.run();
				};
			}
		}
	}

	@Override
	public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof) {
		this.counters.increment(CounterType.LEDGER_PROCESSED);
		synchronized (lock) {
			final VerifiedLedgerHeaderAndProof nextHeader = verifiedCommandsAndProof.getHeader();
			if (headerComparator.compare(nextHeader, this.currentLedgerHeader) <= 0) {
				return;
			}

			Optional<ImmutableList<Command>> verifiedExtension = verifier.verifyAndGetExtension(
				this.currentLedgerHeader.getAccumulatorState(),
				verifiedCommandsAndProof.getCommands(),
				verifiedCommandsAndProof.getHeader().getAccumulatorState()
			);

			if (!verifiedExtension.isPresent()) {
				// This can occur if there is a bug in a commit caller or if there is a quorum of malicious nodes
				throw new IllegalStateException("Accumulator failure " + currentLedgerHeader + " " + verifiedCommandsAndProof);
			}

			// TODO: Add epoch extension verifier, otherwise potential ability to create safety break here with byzantine quorums
			// TODO: since both consensus or sync can be behind in terms of epoch change sync

			VerifiedCommandsAndProof commandsToStore = new VerifiedCommandsAndProof(
				verifiedExtension.get(), verifiedCommandsAndProof.getHeader()
			);

			// persist
			Optional<BFTValidatorSet> validatorSet = this.stateComputer.commit(commandsToStore);

			// TODO: move all of the following to post-persist event handling
			this.currentLedgerHeader = nextHeader;
			this.counters.set(CounterType.LEDGER_STATE_VERSION, this.currentLedgerHeader.getStateVersion());

			verifiedExtension.get().forEach(cmd -> this.mempool.removeCommitted(cmd.getHash()));
			BaseLedgerUpdate ledgerUpdate = new BaseLedgerUpdate(commandsToStore, validatorSet.orElse(null));
			ledgerUpdateSender.sendLedgerUpdate(ledgerUpdate);

			// TODO: Verify headers match
			Collection<LedgerHeader> listeners = this.committedStateSyncers.headMap(
				this.currentLedgerHeader.getAccumulatorState().getStateVersion(), true
			).values();
			Iterator<LedgerHeader> listenersIterator = listeners.iterator();
			while (listenersIterator.hasNext()) {
				LedgerHeader syncTo = listenersIterator.next();
				committedStateSyncSender.sendCommittedStateSync(syncTo);
				listenersIterator.remove();
			}
		}
	}
}
