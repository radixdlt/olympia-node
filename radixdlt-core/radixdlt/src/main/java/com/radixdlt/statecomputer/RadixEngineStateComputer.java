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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.ByzantineQuorumException;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedTxn;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer {
	private static final Logger log = LogManager.getLogger();

	private final RadixEngineMempool mempool;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final View epochCeilingView;
	private final ValidatorSetBuilder validatorSetBuilder;

	private final EventDispatcher<MempoolAddSuccess> mempoolAddSuccessEventDispatcher;
	private final EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher;
	private final EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher;
	private final EventDispatcher<InvalidProposedTxn> invalidProposedCommandEventDispatcher;
	private final EventDispatcher<AtomsCommittedToLedger> committedDispatcher;
	private final SystemCounters systemCounters;

	@Inject
	public RadixEngineStateComputer(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		RadixEngineMempool mempool,
		@EpochCeilingView View epochCeilingView,
		ValidatorSetBuilder validatorSetBuilder,
		EventDispatcher<MempoolAddSuccess> mempoolAddedCommandEventDispatcher,
		EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher,
		EventDispatcher<InvalidProposedTxn> invalidProposedCommandEventDispatcher,
		EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher,
		EventDispatcher<AtomsCommittedToLedger> committedDispatcher,
		SystemCounters systemCounters
	) {
		if (epochCeilingView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.epochCeilingView = epochCeilingView;
		this.validatorSetBuilder = Objects.requireNonNull(validatorSetBuilder);
		this.mempool = Objects.requireNonNull(mempool);
		this.mempoolAddSuccessEventDispatcher = Objects.requireNonNull(mempoolAddedCommandEventDispatcher);
		this.mempoolAddFailureEventDispatcher = Objects.requireNonNull(mempoolAddFailureEventDispatcher);
		this.invalidProposedCommandEventDispatcher = Objects.requireNonNull(invalidProposedCommandEventDispatcher);
		this.mempoolAtomsRemovedEventDispatcher = Objects.requireNonNull(mempoolAtomsRemovedEventDispatcher);
		this.committedDispatcher = Objects.requireNonNull(committedDispatcher);
		this.systemCounters = Objects.requireNonNull(systemCounters);
	}

	public RadixEngine<LedgerAndBFTProof> getEngine() {
		return radixEngine;
	}

	public static class RadixEngineTxn implements PreparedTxn {
		private final Txn txn;
		private final REParsedTxn transaction;
		private final PermissionLevel permissionLevel;

		public RadixEngineTxn(
			Txn txn,
			REParsedTxn transaction,
			PermissionLevel permissionLevel
		) {
			this.txn = txn;
			this.transaction = transaction;
			this.permissionLevel = permissionLevel;
		}

		@Override
		public Txn txn() {
			return txn;
		}
	}

	@Override
	public void addToMempool(Txn txn, @Nullable BFTNode origin) {
		try {
			mempool.add(txn);
			systemCounters.set(SystemCounters.CounterType.MEMPOOL_COUNT, mempool.getCount());
		} catch (MempoolDuplicateException e) {
			var failure = MempoolAddFailure.create(txn, e);
			// Idempotent commands
			log.trace("Mempool duplicate txn: {} origin: {}", txn, origin);
			return;
		} catch (MempoolRejectedException e) {
			var failure = MempoolAddFailure.create(txn, e);
			mempoolAddFailureEventDispatcher.dispatch(failure);
			return;
		}

		mempoolAddSuccessEventDispatcher.dispatch(MempoolAddSuccess.create(txn, origin));
	}

	@Override
	public List<Txn> getNextTxnsFromMempool(List<PreparedTxn> prepared) {
		List<REParsedTxn> cmds = prepared.stream()
			.map(p -> (RadixEngineTxn) p)
			.map(c -> c.transaction)
			.collect(Collectors.toList());

		// TODO: only return commands which will not cause a missing dependency error
		final List<Txn> txns = mempool.getTxns(10, cmds);
		systemCounters.add(SystemCounters.CounterType.MEMPOOL_PROPOSED_TRANSACTION, txns.size());
		return txns;
	}

	private BFTValidatorSet executeSystemUpdate(
		RadixEngineBranch<LedgerAndBFTProof> branch,
		long epoch,
		View view,
		long timestamp,
		ImmutableList.Builder<PreparedTxn> successBuilder
	) {
		final BFTValidatorSet validatorSet;
		if (view.compareTo(epochCeilingView) >= 0) {
			validatorSet = this.validatorSetBuilder.buildValidatorSet(
				branch.getComputedState(RegisteredValidators.class),
				branch.getComputedState(Stakes.class)
			);
		} else {
			validatorSet = null;
		}

		var systemAction = validatorSet == null
			? new SystemNextView(view.number(), timestamp, epoch)
			: new SystemNextEpoch(timestamp, epoch);

		final Txn systemUpdate;
		final List<REParsedTxn> txs;
		try {
			// TODO: combine construct/execute
			systemUpdate = branch.construct(systemAction).buildWithoutSignature();
			txs = branch.execute(List.of(systemUpdate), PermissionLevel.SUPER_USER);
		} catch (RadixEngineException | TxBuilderException e) {
			throw new IllegalStateException(
				String.format("Failed to execute system update:%n%s", e.getMessage()), e
			);
		}
		RadixEngineTxn radixEngineCommand = new RadixEngineTxn(
			systemUpdate,
			txs.get(0),
			PermissionLevel.SUPER_USER
		);
		successBuilder.add(radixEngineCommand);

		return validatorSet;
	}

	private void executeUserCommands(
		RadixEngineBranch<LedgerAndBFTProof> branch,
		List<Txn> nextTxns,
		ImmutableList.Builder<PreparedTxn> successBuilder,
		ImmutableMap.Builder<Txn, Exception> errorBuilder
	) {
		nextTxns.forEach(txn -> {
			final List<REParsedTxn> parsed;
			try {
				parsed = branch.execute(List.of(txn));
			} catch (RadixEngineException e) {
				errorBuilder.put(txn, e);
				invalidProposedCommandEventDispatcher.dispatch(InvalidProposedTxn.create(txn, e));
				return;
			}

			var radixEngineCommand = new RadixEngineTxn(txn, parsed.get(0), PermissionLevel.USER);
			successBuilder.add(radixEngineCommand);
		});
	}

	@Override
	public StateComputerResult prepare(List<PreparedTxn> previous, List<Txn> next, long epoch, View view, long timestamp) {
		var transientBranch = this.radixEngine.transientBranch();
		for (PreparedTxn command : previous) {
			// TODO: fix this cast with generics. Currently the fix would become a bit too messy
			final RadixEngineTxn radixEngineCommand = (RadixEngineTxn) command;
			try {
				transientBranch.execute(
					List.of(radixEngineCommand.txn),
					radixEngineCommand.permissionLevel
				);
			} catch (RadixEngineException e) {
				throw new IllegalStateException("Re-execution of already prepared atom failed: "
					+ radixEngineCommand.transaction.getTxn().getId(), e);
			}
		}

		final ImmutableList.Builder<PreparedTxn> successBuilder = ImmutableList.builder();
		final ImmutableMap.Builder<Txn, Exception> exceptionBuilder = ImmutableMap.builder();
		final BFTValidatorSet validatorSet = this.executeSystemUpdate(transientBranch, epoch, view, timestamp, successBuilder);
		// Don't execute command if changing epochs
		if (validatorSet == null) {
			this.executeUserCommands(transientBranch, next, successBuilder, exceptionBuilder);
		}
		this.radixEngine.deleteBranches();

		return new StateComputerResult(successBuilder.build(), exceptionBuilder.build(), validatorSet);
	}

	private List<REParsedTxn> commitInternal(
		VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState
	) {
		final var atomsToCommit = verifiedTxnsAndProof.getTxns().stream()
			.collect(Collectors.toList());
		var ledgerAndBFTProof = LedgerAndBFTProof.create(
			verifiedTxnsAndProof.getProof(),
			vertexStoreState
		);

		final List<REParsedTxn> radixEngineTxns;
		try {
			radixEngineTxns = this.radixEngine.execute(
				atomsToCommit,
				ledgerAndBFTProof,
				PermissionLevel.SUPER_USER
			);
		} catch (RadixEngineException e) {
			throw new ByzantineQuorumException(String.format("Trying to commit bad atoms:\n%s", atomsToCommit), e);
		}

		radixEngineTxns.forEach(t -> {
			if (t.isUserCommand()) {
				systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_USER_TRANSACTIONS);
			} else {
				systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS);
			}
		});

		return radixEngineTxns;
	}

	@Override
	public void commit(VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState) {
		var txCommitted = commitInternal(verifiedTxnsAndProof, vertexStoreState);

		// TODO: refactor mempool to be less generic and make this more efficient
		// TODO: Move this into engine
		List<Pair<Txn, Exception>> removed = this.mempool.committed(txCommitted);
		systemCounters.set(SystemCounters.CounterType.MEMPOOL_COUNT, mempool.getCount());
		if (!removed.isEmpty()) {
			AtomsRemovedFromMempool atomsRemovedFromMempool = AtomsRemovedFromMempool.create(removed);
			mempoolAtomsRemovedEventDispatcher.dispatch(atomsRemovedFromMempool);
		}

		// Don't send event on genesis
		// TODO: this is a bit hacky
		if (verifiedTxnsAndProof.getProof().getStateVersion() > 0) {
			var txns = verifiedTxnsAndProof.getTxns();
			committedDispatcher.dispatch(AtomsCommittedToLedger.create(txns, txCommitted));
		}
	}
}
