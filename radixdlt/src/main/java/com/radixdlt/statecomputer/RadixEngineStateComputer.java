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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.middleware2.store.StoredCommittedCommand;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.store.berkeley.NextCommittedLimitReachedException;
import com.radixdlt.sync.CommittedReader;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer, CommittedReader {

	// TODO: Refactor committed command when commit logic is re-written
	// TODO: as currently it's mostly loosely coupled logic
	public interface CommittedAtomWithResult {
		CommittedAtom getCommittedAtom();
		CommittedAtomWithResult ifSuccess(Consumer<ImmutableSet<EUID>> successConsumer);
		CommittedAtomWithResult ifError(Consumer<RadixEngineException> errorConsumer);
	}

	// TODO: Remove this temporary interface
	public interface CommittedAtomSender {
		void sendCommittedAtom(CommittedAtomWithResult committedAtomWithResult);
	}

	private final Serialization serialization;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final View epochChangeView;
	private final CommittedCommandsReader committedCommandsReader;
	private final CommittedAtomSender committedAtomSender;
	private final Object lock = new Object();
	private final TreeMap<Long, StoredCommittedCommand> unstoredCommittedAtoms = new TreeMap<>();
	private final TreeMap<Long, VerifiedLedgerHeaderAndProof> epochProofs = new TreeMap<>();

	public RadixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		View epochChangeView,
		CommittedCommandsReader committedCommandsReader,
		CommittedAtomSender committedAtomSender
	) {
		if (epochChangeView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.serialization = Objects.requireNonNull(serialization);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.epochChangeView = epochChangeView;
		this.committedCommandsReader = Objects.requireNonNull(committedCommandsReader);
		this.committedAtomSender = Objects.requireNonNull(committedAtomSender);
	}

	// TODO Move this to a different class class when unstored committed atoms is fixed
	@Override
	public VerifiedCommandsAndProof getNextCommittedCommands(DtoLedgerHeaderAndProof start, int batchSize) {
		if (start.getLedgerHeader().isEndOfEpoch()) {
			long currentEpoch = start.getLedgerHeader().getEpoch() + 1;
			long nextEpoch = currentEpoch + 1;
			VerifiedLedgerHeaderAndProof nextEpochProof = epochProofs.get(nextEpoch);
			if (nextEpochProof == null) {
				return null;
			}

			return new VerifiedCommandsAndProof(ImmutableList.of(), nextEpochProof);
		}

		// TODO: verify start
		long stateVersion = start.getLedgerHeader().getAccumulatorState().getStateVersion();

		// TODO: This may still return an empty list as we still count state versions for atoms which
		// TODO: never make it into the radix engine due to state errors. This is because we only check
		// TODO: validity on commit rather than on proposal/prepare.
		final TreeMap<Long, StoredCommittedCommand> storedCommittedAtoms;
		try {
			storedCommittedAtoms = committedCommandsReader.getNextCommittedCommands(stateVersion, batchSize);
		} catch (NextCommittedLimitReachedException e) {
			return null;
		}

		final VerifiedLedgerHeaderAndProof nextHeader;
		if (storedCommittedAtoms.firstEntry() != null) {
			nextHeader = storedCommittedAtoms.firstEntry().getValue().getStateAndProof();
		} else {
			Entry<Long, StoredCommittedCommand> uncommittedEntry = unstoredCommittedAtoms.higherEntry(stateVersion);
			if (uncommittedEntry == null) {
				return null;
			}
			nextHeader = uncommittedEntry.getValue().getStateAndProof();
		}

		synchronized (lock) {
			final long proofStateVersion = nextHeader.getStateVersion();
			Map<Long, StoredCommittedCommand> unstoredToReturn
				= unstoredCommittedAtoms.subMap(stateVersion, false, proofStateVersion, true);
			storedCommittedAtoms.putAll(unstoredToReturn);
		}

		return new VerifiedCommandsAndProof(
			storedCommittedAtoms.values().stream().map(StoredCommittedCommand::getCommand).collect(ImmutableList.toImmutableList()),
			nextHeader
		);
	}

	@Override
	public StateComputerResult prepare(ImmutableList<Command> uncommittedChain, View view) {
		RadixEngineBranch<LedgerAtom> transientBranch = this.radixEngine.transientBranch();
		Builder<Command> failedCommandsBuilder = ImmutableSet.builder();
		for (Command command : uncommittedChain) {
			ClientAtom clientAtom = mapCommand(command);
			if (clientAtom != null) {
				try {
					transientBranch.checkAndStore(clientAtom);
				} catch (RadixEngineException e) {
					failedCommandsBuilder.add(command);
				}
			}
		}

		ImmutableSet<Command> failedCommands = failedCommandsBuilder.build();

		this.radixEngine.deleteBranches();

		if (view.compareTo(epochChangeView) >= 0) {
			RadixEngineValidatorSetBuilder validatorSetBuilder = transientBranch.getComputedState(RadixEngineValidatorSetBuilder.class);
			return new StateComputerResult(failedCommands, validatorSetBuilder.build());
		}

		return new StateComputerResult(failedCommands);
	}

	private ClientAtom mapCommand(Command command) {
		try {
			return serialization.fromDson(command.getPayload(), ClientAtom.class);
		} catch (DeserializeException e) {
			return null;
		}
	}

	private void commitCommand(long version, Command command, VerifiedLedgerHeaderAndProof proof) {
		boolean storedInRadixEngine = false;
		final ClientAtom clientAtom = this.mapCommand(command);
		if (clientAtom != null) {
			final CommittedAtom committedAtom = new CommittedAtom(clientAtom, version, proof);
			try {
				// TODO: execute list of commands instead
				this.radixEngine.checkAndStore(committedAtom);
				storedInRadixEngine = true;
			} catch (RadixEngineException e) {
				// TODO: Don't check for state computer errors for now so that we don't
				// TODO: have to deal with failing leader proposals
				// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals

				// TODO: move VIRTUAL_STATE_CONFLICT to static check
				committedAtomSender.sendCommittedAtom(CommittedAtoms.error(committedAtom, e));
			}
		}

		if (!storedInRadixEngine) {
			StoredCommittedCommand storedCommittedCommand = new StoredCommittedCommand(
				command,
				proof
			);
			this.unstoredCommittedAtoms.put(version, storedCommittedCommand);
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
