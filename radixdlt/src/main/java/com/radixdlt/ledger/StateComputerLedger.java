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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.Pair;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Synchronizes execution
 */
public final class StateComputerLedger implements Ledger, NextCommandGenerator {
	public static class StateComputerResult {
		private final ImmutableSet<Command> successfulCommands;
		private final ImmutableMap<Command, Exception> failedCommands;
		private final BFTValidatorSet nextValidatorSet;

		public StateComputerResult(
			ImmutableSet<Command> successfulCommands,
			ImmutableMap<Command, Exception> failedCommands,
			BFTValidatorSet nextValidatorSet
		) {
			this.successfulCommands = Objects.requireNonNull(successfulCommands);
			this.failedCommands = Objects.requireNonNull(failedCommands);
			this.nextValidatorSet = nextValidatorSet;
		}

		public StateComputerResult(ImmutableSet<Command> successfulCommands, ImmutableMap<Command, Exception> failedCommands) {
			this(successfulCommands, failedCommands, null);
		}

		Optional<BFTValidatorSet> getNextValidatorSet() {
			return Optional.ofNullable(nextValidatorSet);
		}

		ImmutableSet<Command> getSuccessfulCommands() {
			return successfulCommands;
		}

		ImmutableMap<Command, Exception> getFailedCommands() {
			return failedCommands;
		}
	}

	public interface StateComputer {
		StateComputerResult prepare(ImmutableList<Command> previous, Command next, View view);
		void commit(VerifiedCommandsAndProof verifiedCommandsAndProof);
	}

	public interface LedgerUpdateSender {
		void sendLedgerUpdate(LedgerUpdate ledgerUpdate);
	}

	private final Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private final Mempool mempool;
	private final StateComputer stateComputer;
	private final LedgerUpdateSender ledgerUpdateSender;
	private final SystemCounters counters;
	private final LedgerAccumulator accumulator;
	private final LedgerAccumulatorVerifier verifier;

	private final Object lock = new Object();
	private VerifiedLedgerHeaderAndProof currentLedgerHeader;

	@Inject
	public StateComputerLedger(
		Comparator<VerifiedLedgerHeaderAndProof> headerComparator,
		VerifiedLedgerHeaderAndProof initialLedgerState,
		Mempool mempool,
		StateComputer stateComputer,
		LedgerUpdateSender ledgerUpdateSender,
		LedgerAccumulator accumulator,
		LedgerAccumulatorVerifier verifier,
		SystemCounters counters
	) {
		this.headerComparator = Objects.requireNonNull(headerComparator);
		this.currentLedgerHeader = initialLedgerState;
		this.mempool = Objects.requireNonNull(mempool);
		this.stateComputer = Objects.requireNonNull(stateComputer);
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
	public Optional<PreparedVertex> prepare(LinkedList<PreparedVertex> previous, VerifiedVertex vertex) {
		final LedgerHeader parentHeader = vertex.getParentHeader().getLedgerHeader();
		final AccumulatorState parentAccumulatorState = parentHeader.getAccumulatorState();
		final ImmutableList<Command> prevCommands = previous.stream()
			.flatMap(v -> Stream.concat(v.successfulCommands(), v.errorCommands().map(Pair::getFirst)))
			.filter(Objects::nonNull)
			.collect(ImmutableList.toImmutableList());
		final long timestamp = vertex.getQC().getTimestampedSignatures().weightedTimestamp();

		synchronized (lock) {
			if (this.currentLedgerHeader.getStateVersion() > parentAccumulatorState.getStateVersion()) {
				return Optional.empty();
			}

			// Don't execute atom if in process of epoch change
			if (parentHeader.isEndOfEpoch()) {
				final PreparedVertex preparedVertex = vertex
					.withHeader(parentHeader.updateViewAndTimestamp(vertex.getView(), timestamp))
					.andCommands(ImmutableSet.of(), ImmutableMap.of());
				return Optional.of(preparedVertex);
			}

			final ImmutableList<Command> concatenatedCommands = this.verifier.verifyAndGetExtension(
				this.currentLedgerHeader.getAccumulatorState(),
				prevCommands,
				parentAccumulatorState
			).orElseThrow(() -> new IllegalStateException("Evidence of safety break current: "
				+ this.currentLedgerHeader.getAccumulatorState() + " prepare head: " + parentAccumulatorState)
			);

			final StateComputerResult result = stateComputer.prepare(concatenatedCommands, vertex.getCommand(), vertex.getView());

			final AccumulatorState accumulatorState;
			if (vertex.getCommand() == null) {
				accumulatorState = parentHeader.getAccumulatorState();
			/*} else if (result.getFailedCommands().contains(vertex.getCommand())) {
				accumulatorState = parentHeader.getAccumulatorState();
			 */
			} else {
				accumulatorState = this.accumulator.accumulate(parentHeader.getAccumulatorState(), vertex.getCommand());
			}

			final LedgerHeader ledgerHeader = LedgerHeader.create(
				parentHeader.getEpoch(),
				vertex.getView(),
				accumulatorState,
				timestamp,
				result.getNextValidatorSet().orElse(null)
			);

			return Optional.of(vertex
				.withHeader(ledgerHeader)
				.andCommands(result.getSuccessfulCommands(), result.getFailedCommands())
			);
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
			this.stateComputer.commit(commandsToStore);

			// TODO: move all of the following to post-persist event handling
			this.currentLedgerHeader = nextHeader;
			this.counters.set(CounterType.LEDGER_STATE_VERSION, this.currentLedgerHeader.getStateVersion());

			verifiedExtension.get().forEach(cmd -> this.mempool.removeCommitted(cmd.getHash()));
			BaseLedgerUpdate ledgerUpdate = new BaseLedgerUpdate(commandsToStore, nextHeader.getNextValidatorSet().orElse(null));
			ledgerUpdateSender.sendLedgerUpdate(ledgerUpdate);
		}
	}
}
