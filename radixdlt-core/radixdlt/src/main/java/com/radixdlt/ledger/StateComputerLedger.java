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
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.NextTxnsGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.store.LastProof;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Synchronizes execution
 */
public final class StateComputerLedger implements Ledger, NextTxnsGenerator {

	public interface PreparedTxn {
		Txn txn();
	}

	public static class StateComputerResult {
		private final List<PreparedTxn> preparedTxns;
		private final Map<Txn, Exception> failedCommands;
		private final BFTValidatorSet nextValidatorSet;
		private final Optional<HashCode> nextForkHash;

		public StateComputerResult(
			List<PreparedTxn> preparedTxns,
			Map<Txn, Exception> failedCommands,
			BFTValidatorSet nextValidatorSet,
			Optional<HashCode> nextForkHash
		) {
			this.preparedTxns = Objects.requireNonNull(preparedTxns);
			this.failedCommands = Objects.requireNonNull(failedCommands);
			this.nextValidatorSet = nextValidatorSet;
			this.nextForkHash = Objects.requireNonNull(nextForkHash);
		}

		public StateComputerResult(List<PreparedTxn> preparedTxns, Map<Txn, Exception> failedCommands) {
			this(preparedTxns, failedCommands, null, Optional.empty());
		}

		public Optional<BFTValidatorSet> getNextValidatorSet() {
			return Optional.ofNullable(nextValidatorSet);
		}

		public Optional<HashCode> getNextForkHash() {
			return nextForkHash;
		}

		public List<PreparedTxn> getSuccessfulCommands() {
			return preparedTxns;
		}

		public Map<Txn, Exception> getFailedCommands() {
			return failedCommands;
		}
	}

	public interface StateComputer {
		void addToMempool(MempoolAdd mempoolAdd, BFTNode origin);
		List<Txn> getNextTxnsFromMempool(List<PreparedTxn> prepared);
		StateComputerResult prepare(List<PreparedTxn> previous, VerifiedVertex vertex, long timestamp);
		void commit(VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState);
	}

	private final Comparator<LedgerProof> headerComparator;
	private final StateComputer stateComputer;
	private final EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher;
	private final SystemCounters counters;
	private final LedgerAccumulator accumulator;
	private final LedgerAccumulatorVerifier verifier;
	private final Object lock = new Object();
	private final TimeSupplier timeSupplier;

	private LedgerProof currentLedgerHeader;

	@Inject
	public StateComputerLedger(
		TimeSupplier timeSupplier,
		@LastProof LedgerProof initialLedgerState,
		Comparator<LedgerProof> headerComparator,
		StateComputer stateComputer,
		EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher,
		LedgerAccumulator accumulator,
		LedgerAccumulatorVerifier verifier,
		SystemCounters counters
	) {
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
		this.headerComparator = Objects.requireNonNull(headerComparator);
		this.stateComputer = Objects.requireNonNull(stateComputer);
		this.ledgerUpdateDispatcher = Objects.requireNonNull(ledgerUpdateDispatcher);
		this.counters = Objects.requireNonNull(counters);
		this.accumulator = Objects.requireNonNull(accumulator);
		this.verifier = Objects.requireNonNull(verifier);


		this.currentLedgerHeader = initialLedgerState;
	}

	public RemoteEventProcessor<MempoolAdd> mempoolAddRemoteEventProcessor() {
		return (node, mempoolAdd) -> {
			synchronized (lock) {
				stateComputer.addToMempool(mempoolAdd, node);
			}
		};
	}

	public EventProcessor<MempoolAdd> mempoolAddEventProcessor() {
		return mempoolAdd -> {
			synchronized (lock) {
				stateComputer.addToMempool(mempoolAdd, null);
			}
		};
	}

	@Override
	public List<Txn> generateNextTxns(View view, List<PreparedVertex> prepared) {
		final ImmutableList<PreparedTxn> preparedTxns = prepared.stream()
				.flatMap(PreparedVertex::successfulCommands)
				.collect(ImmutableList.toImmutableList());
		synchronized (lock) {
			return stateComputer.getNextTxnsFromMempool(preparedTxns);
		}
	}

	@Override
	public Optional<PreparedVertex> prepare(LinkedList<PreparedVertex> previous, VerifiedVertex vertex) {
		final LedgerHeader parentHeader = vertex.getParentHeader().getLedgerHeader();
		final AccumulatorState parentAccumulatorState = parentHeader.getAccumulatorState();
		final ImmutableList<PreparedTxn> prevCommands = previous.stream()
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
					.andTxns(ImmutableList.of(), ImmutableMap.of());
				return Optional.of(preparedVertex);
			}

			final var maybeCommands = this.verifier.verifyAndGetExtension(
				this.currentLedgerHeader.getAccumulatorState(),
				prevCommands,
				p -> p.txn().getId().asHashCode(),
				parentAccumulatorState
			);

			// TODO: Write a test to get here
			// Can possibly get here without maliciousness if parent vertex isn't locked by everyone else
			if (maybeCommands.isEmpty()) {
				return Optional.empty();
			}

			final var concatenatedCommands = maybeCommands.get();

			final StateComputerResult result = stateComputer.prepare(
				concatenatedCommands, vertex, quorumTimestamp
			);

			AccumulatorState accumulatorState = parentHeader.getAccumulatorState();
			for (PreparedTxn txn : result.getSuccessfulCommands()) {
				accumulatorState = this.accumulator.accumulate(accumulatorState, txn.txn().getId().asHashCode());
			}

			final LedgerHeader ledgerHeader = LedgerHeader.create(
				parentHeader.getEpoch(),
				vertex.getView(),
				accumulatorState,
				quorumTimestamp,
				result.getNextValidatorSet().orElse(null),
				result.getNextForkHash()
			);

			final long localTimestamp = timeSupplier.currentTime();
			return Optional.of(vertex
				.withHeader(ledgerHeader, localTimestamp)
				.andTxns(result.getSuccessfulCommands(), result.getFailedCommands())
			);
		}
	}

	public EventProcessor<BFTCommittedUpdate> bftCommittedUpdateEventProcessor() {
		return committedUpdate -> {
			final ImmutableList<Txn> txns = committedUpdate.getCommitted().stream()
				.flatMap(PreparedVertex::successfulCommands)
				.map(PreparedTxn::txn)
				.collect(ImmutableList.toImmutableList());
			var proof = committedUpdate.getVertexStoreState().getRootHeader();
			var verifiedTxnsAndProof = VerifiedTxnsAndProof.create(txns, proof);

			// TODO: Make these two atomic (RPNV1-827)
			this.commit(verifiedTxnsAndProof, committedUpdate.getVertexStoreState());
		};
	}

	public EventProcessor<VerifiedTxnsAndProof> syncEventProcessor() {
		return p -> this.commit(p, null);
	}

	private void commit(VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState) {
		synchronized (lock) {
			final LedgerProof nextHeader = verifiedTxnsAndProof.getProof();
			if (headerComparator.compare(nextHeader, this.currentLedgerHeader) <= 0) {
				return;
			}

			var verifiedExtension = verifier.verifyAndGetExtension(
				this.currentLedgerHeader.getAccumulatorState(),
				verifiedTxnsAndProof.getTxns(),
				txn -> txn.getId().asHashCode(),
				verifiedTxnsAndProof.getProof().getAccumulatorState()
			);

			if (verifiedExtension.isEmpty()) {
				throw new ByzantineQuorumException("Accumulator failure " + currentLedgerHeader + " " + verifiedTxnsAndProof);
			}

			var txns = verifiedExtension.get();
			if (vertexStoreState == null) {
				this.counters.add(CounterType.LEDGER_SYNC_COMMANDS_PROCESSED, txns.size());
			} else {
				this.counters.add(CounterType.LEDGER_BFT_COMMANDS_PROCESSED, txns.size());
			}

			var txnsAndProof = VerifiedTxnsAndProof.create(txns, verifiedTxnsAndProof.getProof());

			// persist
			this.stateComputer.commit(txnsAndProof, vertexStoreState);

			// TODO: move all of the following to post-persist event handling
			this.currentLedgerHeader = nextHeader;
			this.counters.set(CounterType.LEDGER_STATE_VERSION, this.currentLedgerHeader.getStateVersion());

			LedgerUpdate ledgerUpdate = new LedgerUpdate(txnsAndProof);
			ledgerUpdateDispatcher.dispatch(ledgerUpdate);
		}
	}
}
