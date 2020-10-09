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
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer {

	// TODO: Refactor committed command when commit logic is re-written
	// TODO: as currently it's mostly loosely coupled logic
	public interface CommittedAtomWithResult {
		CommittedAtom getCommittedAtom();
		CommittedAtomWithResult ifSuccess(Consumer<ImmutableSet<EUID>> successConsumer);
	}

	// TODO: Remove this temporary interface
	public interface CommittedAtomSender {
		void sendCommittedAtom(CommittedAtomWithResult committedAtomWithResult);
	}

	private final Serialization serialization;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final View epochChangeView;

	public RadixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		View epochChangeView
	) {
		if (epochChangeView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.serialization = Objects.requireNonNull(serialization);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.epochChangeView = epochChangeView;
	}

	public static class RadixEngineCommand implements PreparedCommand {
		private final Command command;
		private final ClientAtom clientAtom;

		public RadixEngineCommand(Command command, ClientAtom clientAtom) {
			this.command = command;
			this.clientAtom = clientAtom;
		}

		@Override
		public Command command() {
			return command;
		}
	}

	private void execute(
		RadixEngineBranch<LedgerAtom> branch,
		Command next,
		View view,
		ImmutableList.Builder<PreparedCommand> successBuilder,
		ImmutableMap.Builder<Command, Exception> errorBuilder
	) {
		if (next != null) {
			final RadixEngineCommand radixEngineCommand;
			try {
				ClientAtom clientAtom = mapCommand(next);
				radixEngineCommand = new RadixEngineCommand(next, clientAtom);
				branch.checkAndStore(clientAtom);
			} catch (RadixEngineException | DeserializeException e) {
				errorBuilder.put(next, e);
				return;
			}

			successBuilder.add(radixEngineCommand);
		}
	}

	@Override
	public StateComputerResult prepare(ImmutableList<PreparedCommand> previous, Command next, View view) {
		RadixEngineBranch<LedgerAtom> transientBranch = this.radixEngine.transientBranch();
		for (PreparedCommand command : previous) {
			// TODO: fix this cast with generics. Currently the fix would become a bit too messy
			final RadixEngineCommand radixEngineCommand = (RadixEngineCommand) command;
			final ClientAtom clientAtom = radixEngineCommand.clientAtom;
			try {
				transientBranch.checkAndStore(clientAtom);
			} catch (RadixEngineException e) {
				throw new IllegalStateException("Re-execution of already prepared atom failed", e);
			}
		}

		final ImmutableList.Builder<PreparedCommand> successBuilder = ImmutableList.builder();
		final ImmutableMap.Builder<Command, Exception> exceptionBuilder = ImmutableMap.builder();

		//SystemParticle lastSystemParticle = transientBranch.getComputedState(SystemParticle.class);
		//final SystemParticle nextSystemParticle;

		final BFTValidatorSet validatorSet;
		if (view.compareTo(epochChangeView) >= 0) {
			RadixEngineValidatorSetBuilder validatorSetBuilder = transientBranch.getComputedState(RadixEngineValidatorSetBuilder.class);
			validatorSet = validatorSetBuilder.build();
		} else {
			validatorSet = null;
		}

		this.execute(transientBranch, next, view, successBuilder, exceptionBuilder);

		this.radixEngine.deleteBranches();
		return new StateComputerResult(successBuilder.build(), exceptionBuilder.build(), validatorSet);
	}

	private ClientAtom mapCommand(Command command) throws DeserializeException {
		return serialization.fromDson(command.getPayload(), ClientAtom.class);
	}

	private void commitCommand(long version, Command command, VerifiedLedgerHeaderAndProof proof) {
		try {
			final ClientAtom clientAtom = this.mapCommand(command);
			final CommittedAtom committedAtom = new CommittedAtom(clientAtom, version, proof);
			// TODO: execute list of commands instead
			this.radixEngine.checkAndStore(committedAtom);
		} catch (RadixEngineException | DeserializeException e) {
			// TODO: Remove throwing of exception
			// TODO: Exception could be because of byzantine quorum
			throw new IllegalStateException("Trying to commit bad command", e);
		}
	}

	@Override
	public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof) {
		final VerifiedLedgerHeaderAndProof headerAndProof = verifiedCommandsAndProof.getHeader();
		long stateVersion = headerAndProof.getAccumulatorState().getStateVersion();
		long firstVersion = stateVersion - verifiedCommandsAndProof.getCommands().size() + 1;
		for (int i = 0; i < verifiedCommandsAndProof.getCommands().size(); i++) {
			this.commitCommand(firstVersion + i, verifiedCommandsAndProof.getCommands().get(i), headerAndProof);
		}
	}
}
