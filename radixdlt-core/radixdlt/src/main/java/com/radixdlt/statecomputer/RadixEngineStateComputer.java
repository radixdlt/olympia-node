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
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ParsedTransaction;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.ByzantineQuorumException;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.atom.Atom;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer {
	private static final Logger log = LogManager.getLogger();

	private final Mempool<ParsedTransaction> mempool;
	private final Serialization serialization;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final View epochCeilingView;
	private final ValidatorSetBuilder validatorSetBuilder;

	private final EventDispatcher<MempoolAddSuccess> mempoolAddSuccessEventDispatcher;
	private final EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher;
	private final EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher;
	private final EventDispatcher<InvalidProposedCommand> invalidProposedCommandEventDispatcher;
	private final EventDispatcher<AtomsCommittedToLedger> committedDispatcher;
	private final SystemCounters systemCounters;

	@Inject
	public RadixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		Mempool<ParsedTransaction> mempool,
		@EpochCeilingView View epochCeilingView,
		ValidatorSetBuilder validatorSetBuilder,
		EventDispatcher<MempoolAddSuccess> mempoolAddedCommandEventDispatcher,
		EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher,
		EventDispatcher<InvalidProposedCommand> invalidProposedCommandEventDispatcher,
		EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher,
		EventDispatcher<AtomsCommittedToLedger> committedDispatcher,
		SystemCounters systemCounters
	) {
		if (epochCeilingView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.serialization = Objects.requireNonNull(serialization);
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

	public static class RadixEngineCommand implements PreparedCommand {
		private final Command command;
		private final ParsedTransaction transaction;
		private final PermissionLevel permissionLevel;

		public RadixEngineCommand(
			Command command,
			ParsedTransaction transaction,
			PermissionLevel permissionLevel
		) {
			this.command = command;
			this.transaction = transaction;
			this.permissionLevel = permissionLevel;
		}

		@Override
		public Command command() {
			return command;
		}

		@Override
		public HashCode hash() {
			return command.getId().asHashCode();
		}
	}

	@Override
	public void addToMempool(Command command, @Nullable BFTNode origin) {
		try {
			mempool.add(command);
		} catch (MempoolDuplicateException e) {
			// Idempotent commands
			log.trace("Mempool duplicate command: {} origin: {}", command, origin);
			return;
		} catch (MempoolRejectedException e) {
			mempoolAddFailureEventDispatcher.dispatch(MempoolAddFailure.create(command, e));
			return;
		}

		mempoolAddSuccessEventDispatcher.dispatch(MempoolAddSuccess.create(command, origin));
	}

	@Override
	public Command getNextCommandFromMempool(ImmutableList<PreparedCommand> prepared) {
		List<ParsedTransaction> cmds = prepared.stream()
			.map(p -> (RadixEngineCommand) p)
			.map(c -> c.transaction)
			.collect(Collectors.toList());

		// TODO: only return commands which will not cause a missing dependency error
		final List<Command> commands = mempool.getCommands(1, cmds);
		if (commands.isEmpty()) {
			return null;
		} else {
			systemCounters.increment(SystemCounters.CounterType.MEMPOOL_PROPOSED_TRANSACTION);
			return commands.get(0);
		}
	}

	private BFTValidatorSet executeSystemUpdate(
		RadixEngineBranch<LedgerAndBFTProof> branch,
		long epoch,
		View view,
		long timestamp,
		ImmutableList.Builder<PreparedCommand> successBuilder
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

		var systemUpdateBuilder = branch.getSubstateCache(
			List.of(SystemParticle.class),
			substates -> {
				var builder = TxBuilder.newSystemBuilder(substates);
				try {
					if (validatorSet == null) {
						builder.systemNextView(view.number(), timestamp, epoch);
					} else {
						builder.systemNextEpoch(timestamp, epoch);
					}
				} catch (TxBuilderException e) {
					throw new IllegalStateException("Could not create system update", e);
				}
				return builder;
			});

		final var systemUpdate = systemUpdateBuilder.buildWithoutSignature();
		final List<ParsedTransaction> txs;
		try {
			txs = branch.execute(List.of(systemUpdate), PermissionLevel.SUPER_USER);
		} catch (RadixEngineException e) {
			throw new IllegalStateException(
				String.format("Failed to execute system update:%n%s%n%s", e.getMessage(), systemUpdate.toInstructionsString()),	e
			);
		}
		Command command = new Command(serialization.toDson(systemUpdate, Output.ALL));
		RadixEngineCommand radixEngineCommand = new RadixEngineCommand(
			command,
			txs.get(0),
			PermissionLevel.SUPER_USER
		);
		successBuilder.add(radixEngineCommand);

		return validatorSet;
	}

	private void executeUserCommand(
		RadixEngineBranch<LedgerAndBFTProof> branch,
		Command next,
		ImmutableList.Builder<PreparedCommand> successBuilder,
		ImmutableMap.Builder<Command, Exception> errorBuilder
	) {
		if (next != null) {
			final List<ParsedTransaction> txs;
			try {
				var atom = mapCommand(next);
				txs = branch.execute(List.of(atom));
			} catch (RadixEngineException | DeserializeException e) {
				errorBuilder.put(next, e);
				invalidProposedCommandEventDispatcher.dispatch(InvalidProposedCommand.create(e));
				return;
			}

			var radixEngineCommand = new RadixEngineCommand(next, txs.get(0), PermissionLevel.USER);
			successBuilder.add(radixEngineCommand);
		}
	}


	@Override
	public StateComputerResult prepare(ImmutableList<PreparedCommand> previous, Command next, long epoch, View view, long timestamp) {
		var transientBranch = this.radixEngine.transientBranch();
		for (PreparedCommand command : previous) {
			// TODO: fix this cast with generics. Currently the fix would become a bit too messy
			final RadixEngineCommand radixEngineCommand = (RadixEngineCommand) command;
			try {
				transientBranch.execute(
					List.of(radixEngineCommand.transaction.getAtom()),
					radixEngineCommand.permissionLevel
				);
			} catch (RadixEngineException e) {
				throw new IllegalStateException("Re-execution of already prepared atom failed: "
					+ radixEngineCommand.transaction.getAtomId(), e);
			}
		}

		final ImmutableList.Builder<PreparedCommand> successBuilder = ImmutableList.builder();
		final ImmutableMap.Builder<Command, Exception> exceptionBuilder = ImmutableMap.builder();
		final BFTValidatorSet validatorSet = this.executeSystemUpdate(transientBranch, epoch, view, timestamp, successBuilder);
		// Don't execute command if changing epochs
		if (validatorSet == null) {
			this.executeUserCommand(transientBranch, next, successBuilder, exceptionBuilder);
		}
		this.radixEngine.deleteBranches();

		return new StateComputerResult(successBuilder.build(), exceptionBuilder.build(), validatorSet);
	}

	private Atom mapCommand(Command command) throws DeserializeException {
		return serialization.fromDson(command.getPayload(), Atom.class);
	}

	private List<ParsedTransaction> commitInternal(
		VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState
	) {
		final var atomsToCommit = new ArrayList<Atom>();
		try {
			for (var cmd : verifiedCommandsAndProof.getCommands()) {
				var atom = this.mapCommand(cmd);
				atomsToCommit.add(atom);
			}
		} catch (DeserializeException e) {
			throw new ByzantineQuorumException("Trying to commit bad atom", e);
		}

		var ledgerAndBFTProof = LedgerAndBFTProof.create(
			verifiedCommandsAndProof.getProof(),
			vertexStoreState
		);

		final List<ParsedTransaction> parsedTransactions;
		try {
			parsedTransactions = this.radixEngine.execute(
				atomsToCommit,
				ledgerAndBFTProof,
				PermissionLevel.SUPER_USER
			);
		} catch (RadixEngineException e) {
			throw new ByzantineQuorumException(String.format("Trying to commit bad atoms:\n%s", atomsToCommit), e);
		}

		parsedTransactions.forEach(t -> {
			if (t.isUserCommand()) {
				systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_USER_TRANSACTIONS);
			} else {
				systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS);
			}
		});

		return parsedTransactions;
	}

	@Override
	public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState) {
		var txCommitted = commitInternal(verifiedCommandsAndProof, vertexStoreState);

		// TODO: refactor mempool to be less generic and make this more efficient
		// TODO: Move this into engine
		List<Pair<Command, Exception>> removed = this.mempool.committed(txCommitted);
		if (!removed.isEmpty()) {
			AtomsRemovedFromMempool atomsRemovedFromMempool = AtomsRemovedFromMempool.create(removed);
			mempoolAtomsRemovedEventDispatcher.dispatch(atomsRemovedFromMempool);
		}

		// Don't send event on genesis
		// TODO: this is a bit hacky
		if (verifiedCommandsAndProof.getProof().getStateVersion() > 0) {
			var cmds = verifiedCommandsAndProof.getCommands();
			committedDispatcher.dispatch(AtomsCommittedToLedger.create(cmds, txCommitted));
		}
	}
}
