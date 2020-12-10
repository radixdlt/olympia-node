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
import com.google.common.hash.HashCode;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.ByzantineQuorumException;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
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
	private final View epochCeilingView;
	private final ValidatorSetBuilder validatorSetBuilder;
	private final Hasher hasher;
	private final RadixEngineAtomicCommitManager atomicCommitManager;

	private RadixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		RadixEngineAtomicCommitManager atomicCommitManager,
		View epochCeilingView,
		ValidatorSetBuilder validatorSetBuilder,
		Hasher hasher
	) {
		this.serialization = Objects.requireNonNull(serialization);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.epochCeilingView = epochCeilingView;
		this.validatorSetBuilder = Objects.requireNonNull(validatorSetBuilder);
		this.hasher = Objects.requireNonNull(hasher);
		this.atomicCommitManager = Objects.requireNonNull(atomicCommitManager);
	}

	public static RadixEngineStateComputer create(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		RadixEngineAtomicCommitManager atomicCommitManager,
		@EpochCeilingView View epochCeilingView,
		ValidatorSetBuilder validatorSetBuilder,
		Hasher hasher
	) {
		if (epochCeilingView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		return new RadixEngineStateComputer(
			serialization,
			radixEngine,
			atomicCommitManager,
			epochCeilingView,
			validatorSetBuilder,
			hasher
		);
	}

	public static class RadixEngineCommand implements PreparedCommand {
		private final Command command;
		private final HashCode hash;
		private final ClientAtom clientAtom;
		private final PermissionLevel permissionLevel;

		public RadixEngineCommand(
			Command command,
			HashCode hash,
			ClientAtom clientAtom,
			PermissionLevel permissionLevel
		) {
			this.command = command;
			this.hash = hash;
			this.clientAtom = clientAtom;
			this.permissionLevel = permissionLevel;
		}

		@Override
		public Command command() {
			return command;
		}

		@Override
		public HashCode hash() {
			return hash;
		}
	}

	private BFTValidatorSet executeSystemUpdate(
		RadixEngineBranch<LedgerAtom> branch,
		long epoch,
		View view,
		long timestamp,
		ImmutableList.Builder<PreparedCommand> successBuilder
	) {
		final SystemParticle lastSystemParticle = branch.getComputedState(SystemParticle.class);
		if (lastSystemParticle.getEpoch() != epoch) {
			throw new IllegalStateException(
				String.format(
					"Consensus epoch(%s) and computer epoch(%s) out of synchrony",
					epoch,
					lastSystemParticle.getEpoch()
				)
			);
		}

		final BFTValidatorSet validatorSet;
		if (view.compareTo(epochCeilingView) >= 0) {
			validatorSet = this.validatorSetBuilder.buildValidatorSet(
				branch.getComputedState(RadixEngineValidatorsComputer.class),
				branch.getComputedState(RadixEngineStakeComputer.class)
			);
		} else {
			validatorSet = null;
		}

		final SystemParticle nextSystemParticle = (validatorSet == null)
			? new SystemParticle(lastSystemParticle.getEpoch(), view.number(), timestamp)
			: new SystemParticle(lastSystemParticle.getEpoch() + 1, 0, timestamp);

		final ClientAtom systemUpdate = ClientAtom.create(
			ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(lastSystemParticle, Spin.UP),
				CMMicroInstruction.checkSpinAndPush(nextSystemParticle, Spin.NEUTRAL),
				CMMicroInstruction.particleGroup()
			),
			hasher
		);
		try {
			branch.checkAndStore(systemUpdate, PermissionLevel.SUPER_USER);
		} catch (RadixEngineException e) {
			throw new IllegalStateException(String.format("Failed to execute system update:%n%s", systemUpdate.toInstructionsString()), e);
		}
		Command command = new Command(serialization.toDson(systemUpdate, Output.ALL));
		RadixEngineCommand radixEngineCommand = new RadixEngineCommand(
			command,
			hasher.hash(command),
			systemUpdate,
			PermissionLevel.SUPER_USER
		);
		successBuilder.add(radixEngineCommand);

		return validatorSet;
	}

	private void executeUserCommand(
		RadixEngineBranch<LedgerAtom> branch,
		Command next,
		ImmutableList.Builder<PreparedCommand> successBuilder,
		ImmutableMap.Builder<Command, Exception> errorBuilder
	) {
		if (next != null) {
			final RadixEngineCommand radixEngineCommand;
			try {
				ClientAtom clientAtom = mapCommand(next);
				HashCode hash = hasher.hash(next);
				radixEngineCommand = new RadixEngineCommand(next, hash, clientAtom, PermissionLevel.USER);
				branch.checkAndStore(clientAtom);
			} catch (RadixEngineException | DeserializeException e) {
				errorBuilder.put(next, e);
				return;
			}

			successBuilder.add(radixEngineCommand);
		}
	}

	@Override
	public StateComputerResult prepare(ImmutableList<PreparedCommand> previous, Command next, long epoch, View view, long timestamp) {
		RadixEngineBranch<LedgerAtom> transientBranch = this.radixEngine.transientBranch();
		for (PreparedCommand command : previous) {
			// TODO: fix this cast with generics. Currently the fix would become a bit too messy
			final RadixEngineCommand radixEngineCommand = (RadixEngineCommand) command;
			try {
				transientBranch.checkAndStore(
					radixEngineCommand.clientAtom,
					radixEngineCommand.permissionLevel
				);
			} catch (RadixEngineException e) {
				throw new IllegalStateException("Re-execution of already prepared atom failed: " + radixEngineCommand.clientAtom, e);
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

	private ClientAtom mapCommand(Command command) throws DeserializeException {
		return serialization.fromDson(command.getPayload(), ClientAtom.class);
	}

	private void commitCommand(long version, Command command, VerifiedLedgerHeaderAndProof proof) {
		final ClientAtom clientAtom;
		try {
			clientAtom = this.mapCommand(command);
		} catch (DeserializeException e) {
			throw new ByzantineQuorumException("Trying to commit bad atom", e);
		}

		try {
			final CommittedAtom committedAtom = new CommittedAtom(clientAtom, version, proof);
			// TODO: execute list of commands instead
			// TODO: Include permission level in committed command
			this.radixEngine.checkAndStore(committedAtom, PermissionLevel.SUPER_USER);
		} catch (RadixEngineException e) {
			throw new ByzantineQuorumException(String.format("Trying to commit bad atom:\n%s", clientAtom.toInstructionsString()), e);
		}
	}

	private void commitInternal(VerifiedCommandsAndProof verifiedCommandsAndProof) {
		final SystemParticle lastSystemParticle = radixEngine.getComputedState(SystemParticle.class);
		final long currentEpoch = lastSystemParticle.getEpoch();
		boolean epochChange = false;

		final VerifiedLedgerHeaderAndProof headerAndProof = verifiedCommandsAndProof.getHeader();
		long stateVersion = headerAndProof.getAccumulatorState().getStateVersion();
		long firstVersion = stateVersion - verifiedCommandsAndProof.getCommands().size() + 1;

		for (int i = 0; i < verifiedCommandsAndProof.getCommands().size(); i++) {
			this.commitCommand(firstVersion + i, verifiedCommandsAndProof.getCommands().get(i), headerAndProof);

			final long nextEpoch = radixEngine.getComputedState(SystemParticle.class).getEpoch();
			final boolean isLastCommand = i == verifiedCommandsAndProof.getCommands().size() - 1;
			final boolean changingEpoch = nextEpoch > currentEpoch;
			if (isLastCommand && changingEpoch) {
				epochChange = true;
			} else if (changingEpoch) {
				throw new ByzantineQuorumException("change of epoch did not occur on last command");
			}
		}

		// Verify that output of radix engine and signed output match
		// TODO: Always follow radix engine as its a deeper source of truth and just mark validator
		// TODO: set as malicious (RPNV1-633)
		if (epochChange) {
			final var reNextValidatorSet = this.validatorSetBuilder.buildValidatorSet(
				this.radixEngine.getComputedState(RadixEngineValidatorsComputer.class),
				this.radixEngine.getComputedState(RadixEngineStakeComputer.class)
			);
			final var signedValidatorSet = verifiedCommandsAndProof.getHeader().getNextValidatorSet()
				.orElseThrow(() -> new ByzantineQuorumException("RE has changed epochs but proofs don't show."));
			if (!signedValidatorSet.equals(reNextValidatorSet)) {
				throw new ByzantineQuorumException("RE validator set does not agree with signed validator set");
			}
		} else {
			if (verifiedCommandsAndProof.getHeader().getNextValidatorSet().isPresent()) {
				throw new ByzantineQuorumException("Trying to change epochs when RE isn't");
			}
		}
	}

	@Override
	public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState) {
		atomicCommitManager.startTransaction();
		try {
			commitInternal(verifiedCommandsAndProof);
		} catch (Exception e) {
			atomicCommitManager.abortTransaction();
			// At this point the radix engine has no mechanism to recover from byzantine quorum failure
			// TODO: resolve issues with byzantine quorum (RPNV1-828)
			throw e;
		}
		if (vertexStoreState != null) {
			atomicCommitManager.save(vertexStoreState);
		}
		atomicCommitManager.commitTransaction();
	}
}
