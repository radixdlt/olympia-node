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
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;
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
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.atom.LedgerAtom;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer {
	private static final Logger log = LogManager.getLogger();

	private final Mempool<Atom> mempool;
	private final Serialization serialization;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final View epochCeilingView;
	private final ValidatorSetBuilder validatorSetBuilder;
	private final Hasher hasher;
	private final RadixEngineAtomicCommitManager atomicCommitManager;

	private final EventDispatcher<MempoolAddSuccess> mempoolAddSuccessEventDispatcher;
	private final EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher;
	private final EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher;
	private final EventDispatcher<InvalidProposedCommand> invalidProposedCommandEventDispatcher;
	private final SystemCounters systemCounters;

	@Inject
	public RadixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		Mempool<Atom> mempool,
		RadixEngineAtomicCommitManager atomicCommitManager,
		@EpochCeilingView View epochCeilingView,
		ValidatorSetBuilder validatorSetBuilder,
		Hasher hasher,
		EventDispatcher<MempoolAddSuccess> mempoolAddedCommandEventDispatcher,
		EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher,
		EventDispatcher<InvalidProposedCommand> invalidProposedCommandEventDispatcher,
		EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher,
		SystemCounters systemCounters
	) {
		if (epochCeilingView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.serialization = Objects.requireNonNull(serialization);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.epochCeilingView = epochCeilingView;
		this.validatorSetBuilder = Objects.requireNonNull(validatorSetBuilder);
		this.hasher = Objects.requireNonNull(hasher);
		this.atomicCommitManager = Objects.requireNonNull(atomicCommitManager);
		this.mempool = Objects.requireNonNull(mempool);
		this.mempoolAddSuccessEventDispatcher = Objects.requireNonNull(mempoolAddedCommandEventDispatcher);
		this.mempoolAddFailureEventDispatcher = Objects.requireNonNull(mempoolAddFailureEventDispatcher);
		this.invalidProposedCommandEventDispatcher = Objects.requireNonNull(invalidProposedCommandEventDispatcher);
		this.mempoolAtomsRemovedEventDispatcher = Objects.requireNonNull(mempoolAtomsRemovedEventDispatcher);
		this.systemCounters = Objects.requireNonNull(systemCounters);
	}

	public static class RadixEngineCommand implements PreparedCommand {
		private final Command command;
		private final HashCode hash;
		private final Atom atom;
		private final PermissionLevel permissionLevel;

		public RadixEngineCommand(
			Command command,
			HashCode hash,
			Atom atom,
			PermissionLevel permissionLevel
		) {
			this.command = command;
			this.hash = hash;
			this.atom = atom;
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

	@Override
	public void addToMempool(Command command, @Nullable BFTNode origin) {
		Atom atom = command.map(payload -> {
			try {
				return serialization.fromDson(payload, Atom.class);
			} catch (DeserializeException e) {
				return null;
			}
		});
		if (atom == null) {
			mempoolAddFailureEventDispatcher.dispatch(
				MempoolAddFailure.create(command, new MempoolRejectedException("Bad atom"))
			);
			return;
		}

		try {
			mempool.add(atom);
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
		Set<Atom> preparedAtoms = prepared.stream()
			.map(p -> (RadixEngineCommand) p)
			.map(c -> c.atom)
			.collect(Collectors.toSet());

		// TODO: only return commands which will not cause a missing dependency error
		final List<Atom> commands = mempool.getCommands(1, preparedAtoms);
		if (commands.isEmpty()) {
			return null;
		} else {
			systemCounters.increment(SystemCounters.CounterType.MEMPOOL_PROPOSED_TRANSACTION);
			byte[] dson = serialization.toDson(commands.get(0), Output.ALL);
			return new Command(dson);
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
				branch.getComputedState(RegisteredValidators.class),
				branch.getComputedState(Stakes.class)
			);
		} else {
			validatorSet = null;
		}

		final SystemParticle nextSystemParticle = (validatorSet == null)
			? new SystemParticle(lastSystemParticle.getEpoch(), view.number(), timestamp)
			: new SystemParticle(lastSystemParticle.getEpoch() + 1, 0, timestamp);

		final Atom systemUpdate = Atom.newBuilder().addParticleGroup(
			ParticleGroup.builder()
				.spinDown(lastSystemParticle)
				.spinUp(nextSystemParticle)
				.build()
		).buildAtom();
		try {
			branch.execute(systemUpdate, PermissionLevel.SUPER_USER);
		} catch (RadixEngineException e) {
			throw new IllegalStateException(
				String.format("Failed to execute system update:%n%s%n%s", e.getMessage(), systemUpdate.toInstructionsString()),	e
			);
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
				Atom atom = mapCommand(next);
				HashCode hash = hasher.hash(next);
				radixEngineCommand = new RadixEngineCommand(next, hash, atom, PermissionLevel.USER);
				branch.execute(atom);
			} catch (RadixEngineException | DeserializeException e) {
				errorBuilder.put(next, e);
				invalidProposedCommandEventDispatcher.dispatch(InvalidProposedCommand.create(e));
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
				transientBranch.execute(
					radixEngineCommand.atom,
					radixEngineCommand.permissionLevel
				);
			} catch (RadixEngineException e) {
				throw new IllegalStateException("Re-execution of already prepared atom failed: " + radixEngineCommand.atom, e);
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

	private void commitCommand(long version, Atom atom, VerifiedLedgerHeaderAndProof proof) {
		try {
			final CommittedAtom committedAtom;
			if (proof.getStateVersion() == version) {
				committedAtom = CommittedAtom.create(atom, proof);
			} else {
				committedAtom = CommittedAtom.create(atom, version);
			}
			// TODO: execute list of commands instead
			// TODO: Include permission level in committed command
			this.radixEngine.execute(committedAtom, PermissionLevel.SUPER_USER);
		} catch (RadixEngineException e) {
			throw new ByzantineQuorumException(String.format("Trying to commit bad atom:\n%s", atom.toInstructionsString()), e);
		}

		final boolean isUserCommand = atom.upParticles().noneMatch(p -> p instanceof SystemParticle);
		if (isUserCommand) {
			systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_USER_TRANSACTIONS);
		} else {
			systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS);
		}
	}

	private List<Atom> commitInternal(VerifiedCommandsAndProof verifiedCommandsAndProof) {
		final SystemParticle lastSystemParticle = radixEngine.getComputedState(SystemParticle.class);
		final long currentEpoch = lastSystemParticle.getEpoch();
		boolean epochChange = false;

		final VerifiedLedgerHeaderAndProof headerAndProof = verifiedCommandsAndProof.getHeader();
		long stateVersion = headerAndProof.getAccumulatorState().getStateVersion();
		long firstVersion = stateVersion - verifiedCommandsAndProof.getCommands().size() + 1;

		final var atomsToCommit = new ArrayList<Atom>();
		try {
			for (var cmd : verifiedCommandsAndProof.getCommands()) {
				atomsToCommit.add(this.mapCommand(cmd));
			}
		} catch (DeserializeException e) {
			throw new ByzantineQuorumException("Trying to commit bad atom", e);
		}

		for (int i = 0; i < verifiedCommandsAndProof.getCommands().size(); i++) {
			this.commitCommand(firstVersion + i, atomsToCommit.get(i), headerAndProof);

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
				this.radixEngine.getComputedState(RegisteredValidators.class),
				this.radixEngine.getComputedState(Stakes.class)
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

		return atomsToCommit;
	}

	@Override
	public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState) {
	    List<Atom> atomsCommitted;
		atomicCommitManager.startTransaction();
		try {
			atomsCommitted = commitInternal(verifiedCommandsAndProof);
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

		// TODO: refactor mempool to be less generic and make this more efficient
		List<Pair<Atom, Exception>> removed = this.mempool.committed(atomsCommitted);
		if (!removed.isEmpty()) {
			AtomsRemovedFromMempool atomsRemovedFromMempool = AtomsRemovedFromMempool.create(removed);
			mempoolAtomsRemovedEventDispatcher.dispatch(atomsRemovedFromMempool);
		}
	}
}
