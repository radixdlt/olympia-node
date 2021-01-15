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
import com.google.inject.Inject;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.store.LastProof;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Synchronizes execution
 */
public final class StateComputerLedger implements Ledger, NextCommandGenerator {

	public interface PreparedCommand {
		Command command();

		HashCode hash();
	}

	public static class StateComputerResult {
		private final ImmutableList<PreparedCommand> preparedCommands;
		private final ImmutableMap<Command, Exception> failedCommands;
		private final BFTValidatorSet nextValidatorSet;

		public StateComputerResult(
			ImmutableList<PreparedCommand> preparedCommands,
			ImmutableMap<Command, Exception> failedCommands,
			BFTValidatorSet nextValidatorSet
		) {
			this.preparedCommands = Objects.requireNonNull(preparedCommands);
			this.failedCommands = Objects.requireNonNull(failedCommands);
			this.nextValidatorSet = nextValidatorSet;
		}

		public StateComputerResult(ImmutableList<PreparedCommand> preparedCommands, ImmutableMap<Command, Exception> failedCommands) {
			this(preparedCommands, failedCommands, null);
		}

		public Optional<BFTValidatorSet> getNextValidatorSet() {
			return Optional.ofNullable(nextValidatorSet);
		}

		public ImmutableList<PreparedCommand> getSuccessfulCommands() {
			return preparedCommands;
		}

		public ImmutableMap<Command, Exception> getFailedCommands() {
			return failedCommands;
		}
	}

	public interface StateComputer {
		void addToMempool(Command command);
		Command getNextCommandFromMempool(Set<HashCode> exclude);
		StateComputerResult prepare(ImmutableList<PreparedCommand> previous, Command next, long epoch, View view, long timestamp);
		void commit(VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState);
	}

	public interface LedgerUpdateSender {
		void sendLedgerUpdate(LedgerUpdate ledgerUpdate);
	}

	private final Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private final StateComputer stateComputer;
	private final LedgerUpdateSender ledgerUpdateSender;
	private final SystemCounters counters;
	private final LedgerAccumulator accumulator;
	private final LedgerAccumulatorVerifier verifier;
	private final Hasher hasher;
	private final Object lock = new Object();
	private final TimeSupplier timeSupplier;

	private VerifiedLedgerHeaderAndProof currentLedgerHeader;

	@Inject
	public StateComputerLedger(
		TimeSupplier timeSupplier,
		@LastProof VerifiedLedgerHeaderAndProof initialLedgerState,
		Comparator<VerifiedLedgerHeaderAndProof> headerComparator,
		StateComputer stateComputer,
		LedgerUpdateSender ledgerUpdateSender,
		LedgerAccumulator accumulator,
		LedgerAccumulatorVerifier verifier,
		SystemCounters counters,
		Hasher hasher
	) {
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
		this.headerComparator = Objects.requireNonNull(headerComparator);
		this.currentLedgerHeader = initialLedgerState;
		this.stateComputer = Objects.requireNonNull(stateComputer);
		this.ledgerUpdateSender = Objects.requireNonNull(ledgerUpdateSender);
		this.counters = Objects.requireNonNull(counters);
		this.accumulator = Objects.requireNonNull(accumulator);
		this.verifier = Objects.requireNonNull(verifier);
		this.hasher = Objects.requireNonNull(hasher);
	}

	public EventProcessor<MempoolAdd> mempoolAddEventProcessor() {
		return (mempoolAdd) -> {
			synchronized (lock) {
				stateComputer.addToMempool(mempoolAdd.getCommand());
			}
		};
	}

	@Override
	public Command generateNextCommand(View view, Set<HashCode> prepared) {
		return stateComputer.getNextCommandFromMempool(prepared);
	}

	@Override
	public Optional<PreparedVertex> prepare(LinkedList<PreparedVertex> previous, VerifiedVertex vertex) {
		final LedgerHeader parentHeader = vertex.getParentHeader().getLedgerHeader();
		final AccumulatorState parentAccumulatorState = parentHeader.getAccumulatorState();
		final ImmutableList<PreparedCommand> prevCommands = previous.stream()
			.flatMap(PreparedVertex::successfulCommands)
			.collect(ImmutableList.toImmutableList());
		final long quorumTimestamp;
		// if vertex has genesis parent then QC is mocked so just use previous timestamp
		// this does have the edge case of never increasing timestamps if configuration is
		// one view per epoch but good enough for now
		if (vertex.getParentHeader().getView().isGenesis()) {
			quorumTimestamp = vertex.getParentHeader().getLedgerHeader().timestamp();
		} else {
			quorumTimestamp = vertex.getQC().getTimestampedSignatures().weightedTimestamp();
		}


		synchronized (lock) {
			if (this.currentLedgerHeader.getStateVersion() > parentAccumulatorState.getStateVersion()) {
				return Optional.empty();
			}

			// Don't execute atom if in process of epoch change
			if (parentHeader.isEndOfEpoch()) {
				final long localTimestamp = timeSupplier.currentTime();
				final PreparedVertex preparedVertex = vertex
					.withHeader(parentHeader.updateViewAndTimestamp(vertex.getView(), quorumTimestamp), localTimestamp)
					.andCommands(ImmutableList.of(), ImmutableMap.of());
				return Optional.of(preparedVertex);
			}

			final Optional<ImmutableList<PreparedCommand>> maybeCommands = this.verifier.verifyAndGetExtension(
				this.currentLedgerHeader.getAccumulatorState(),
				prevCommands,
				PreparedCommand::hash,
				parentAccumulatorState
			);

			// TODO: Write a test to get here
			// Can possibly get here without maliciousness if parent vertex isn't locked by everyone else
			if (maybeCommands.isEmpty()) {
				return Optional.empty();
			}

			final ImmutableList<PreparedCommand> concatenatedCommands = maybeCommands.get();

			final StateComputerResult result = stateComputer.prepare(
				concatenatedCommands,
				vertex.getCommand().orElse(null),
				vertex.getParentHeader().getLedgerHeader().getEpoch(),
				vertex.getView(),
				quorumTimestamp
			);

			AccumulatorState accumulatorState = parentHeader.getAccumulatorState();
			for (PreparedCommand cmd : result.getSuccessfulCommands()) {
				accumulatorState = this.accumulator.accumulate(accumulatorState, cmd.hash());
			}

			final LedgerHeader ledgerHeader = LedgerHeader.create(
				parentHeader.getEpoch(),
				vertex.getView(),
				accumulatorState,
				quorumTimestamp,
				result.getNextValidatorSet().orElse(null)
			);

			final long localTimestamp = timeSupplier.currentTime();
			return Optional.of(vertex
				.withHeader(ledgerHeader, localTimestamp)
				.andCommands(result.getSuccessfulCommands(), result.getFailedCommands())
			);
		}
	}

	public EventProcessor<BFTCommittedUpdate> bftCommittedUpdateEventProcessor() {
		return committedUpdate -> {
			final ImmutableList<Command> commands = committedUpdate.getCommitted().stream()
				.flatMap(PreparedVertex::successfulCommands)
				.map(PreparedCommand::command)
				.collect(ImmutableList.toImmutableList());
			VerifiedLedgerHeaderAndProof proof = committedUpdate.getVertexStoreState().getRootHeader();
			VerifiedCommandsAndProof verifiedCommandsAndProof = new VerifiedCommandsAndProof(commands, proof);

			// TODO: Make these two atomic (RPNV1-827)
			this.commit(verifiedCommandsAndProof, committedUpdate.getVertexStoreState());
		};
	}

	public EventProcessor<VerifiedCommandsAndProof> syncEventProcessor() {
		return p -> this.commit(p, null);
	}

	private void commit(VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState) {
		this.counters.increment(CounterType.LEDGER_PROCESSED);
		synchronized (lock) {
			final VerifiedLedgerHeaderAndProof nextHeader = verifiedCommandsAndProof.getHeader();
			if (headerComparator.compare(nextHeader, this.currentLedgerHeader) <= 0) {
				return;
			}

			Optional<ImmutableList<Command>> verifiedExtension = verifier.verifyAndGetExtension(
				this.currentLedgerHeader.getAccumulatorState(),
				verifiedCommandsAndProof.getCommands(),
				hasher::hash,
				verifiedCommandsAndProof.getHeader().getAccumulatorState()
			);

			if (!verifiedExtension.isPresent()) {
				throw new ByzantineQuorumException("Accumulator failure " + currentLedgerHeader + " " + verifiedCommandsAndProof);
			}

			ImmutableList<Command> commands = verifiedExtension.get();
			if (vertexStoreState == null) {
				this.counters.add(CounterType.LEDGER_SYNC_COMMANDS_PROCESSED, commands.size());
			} else {
				this.counters.add(CounterType.LEDGER_BFT_COMMANDS_PROCESSED, commands.size());
			}

			VerifiedCommandsAndProof commandsToStore = new VerifiedCommandsAndProof(
				commands, verifiedCommandsAndProof.getHeader()
			);

			// persist
			this.stateComputer.commit(commandsToStore, vertexStoreState);

			// TODO: move all of the following to post-persist event handling
			this.currentLedgerHeader = nextHeader;
			this.counters.set(CounterType.LEDGER_STATE_VERSION, this.currentLedgerHeader.getStateVersion());

			BaseLedgerUpdate ledgerUpdate = new BaseLedgerUpdate(commandsToStore);
			ledgerUpdateSender.sendLedgerUpdate(ledgerUpdate);
		}
	}
}
