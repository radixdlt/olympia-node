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

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.radixdlt.application.system.NextValidatorSetEvent;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.atom.actions.NextRound;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.MetadataException;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.RadixEngineResult;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.ByzantineQuorumException;
import com.radixdlt.ledger.CommittedBadTxnException;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedTxn;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.statecomputer.forks.Forks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer {
	private static final Logger log = LogManager.getLogger();

	private final RadixEngineMempool mempool;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher;
	private final EventDispatcher<MempoolAddSuccess> mempoolAddSuccessEventDispatcher;
	private final EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher;
	private final EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher;
	private final EventDispatcher<InvalidProposedTxn> invalidProposedCommandEventDispatcher;
	private final SystemCounters systemCounters;
	private final Hasher hasher;
	private final Forks forks;

	private ProposerElection proposerElection;
	private View epochCeilingView;
	private OptionalInt maxSigsPerRound;

	@Inject
	public RadixEngineStateComputer(
		ProposerElection proposerElection, // TODO: Should be able to load this directly from state
		RadixEngine<LedgerAndBFTProof> radixEngine,
		RadixEngineMempool mempool, // TODO: Move this into radixEngine
		@EpochCeilingView View epochCeilingView, // TODO: Move this into radixEngine
		@MaxSigsPerRound OptionalInt maxSigsPerRound, // TODO: Move this into radixEngine
		EventDispatcher<MempoolAddSuccess> mempoolAddedCommandEventDispatcher,
		EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher,
		EventDispatcher<InvalidProposedTxn> invalidProposedCommandEventDispatcher,
		EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher,
		EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher,
		Hasher hasher,
		SystemCounters systemCounters,
		Forks forks
	) {
		if (epochCeilingView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.epochCeilingView = epochCeilingView;
		this.maxSigsPerRound = maxSigsPerRound;
		this.mempool = Objects.requireNonNull(mempool);
		this.mempoolAddSuccessEventDispatcher = Objects.requireNonNull(mempoolAddedCommandEventDispatcher);
		this.mempoolAddFailureEventDispatcher = Objects.requireNonNull(mempoolAddFailureEventDispatcher);
		this.invalidProposedCommandEventDispatcher = Objects.requireNonNull(invalidProposedCommandEventDispatcher);
		this.mempoolAtomsRemovedEventDispatcher = Objects.requireNonNull(mempoolAtomsRemovedEventDispatcher);
		this.ledgerUpdateDispatcher = Objects.requireNonNull(ledgerUpdateDispatcher);
		this.hasher = Objects.requireNonNull(hasher);
		this.systemCounters = Objects.requireNonNull(systemCounters);
		this.proposerElection = proposerElection;
		this.forks = Objects.requireNonNull(forks);
	}

	public static class RadixEngineTxn implements PreparedTxn {
		private final Txn txn;
		private final REProcessedTxn processed;
		private final PermissionLevel permissionLevel;

		public RadixEngineTxn(
			Txn txn,
			REProcessedTxn processed,
			PermissionLevel permissionLevel
		) {
			this.txn = txn;
			this.processed = processed;
			this.permissionLevel = permissionLevel;
		}

		REProcessedTxn processedTxn() {
			return processed;
		}

		@Override
		public Txn txn() {
			return txn;
		}
	}

	@Override
	public void addToMempool(MempoolAdd mempoolAdd, @Nullable BFTNode origin) {
		mempoolAdd.getTxns().forEach(txn -> {
			try {
				mempool.add(txn);
				systemCounters.set(SystemCounters.CounterType.MEMPOOL_COUNT, mempool.getCount());
			} catch (MempoolDuplicateException e) {
				// Idempotent commands
				log.trace("Mempool duplicate txn: {} origin: {}", txn, origin);
				return;
			} catch (MempoolRejectedException e) {
				var failure = MempoolAddFailure.create(txn, e, origin);
				mempoolAddFailureEventDispatcher.dispatch(failure);
				mempoolAdd.onFailure(e); // Required for blocking web apis
				return;
			}

			var success = MempoolAddSuccess.create(txn, origin);
			mempoolAdd.onSuccess(success); // Required for blocking web apis
			mempoolAddSuccessEventDispatcher.dispatch(success);
		});
	}

	@Override
	public List<Txn> getNextTxnsFromMempool(List<PreparedTxn> prepared) {
		List<REProcessedTxn> cmds = prepared.stream()
			.map(p -> (RadixEngineTxn) p)
			.map(RadixEngineTxn::processedTxn)
			.collect(Collectors.toList());

		// TODO: only return commands which will not cause a missing dependency error
		final List<Txn> txns = mempool.getTxns(maxSigsPerRound.orElse(50), cmds);
		systemCounters.add(SystemCounters.CounterType.MEMPOOL_PROPOSED_TRANSACTION, txns.size());
		return txns;
	}

	private LongFunction<ECPublicKey> getValidatorMapping() {
		return l -> proposerElection.getProposer(View.of(l)).getKey();
	}

	private RadixEngineTxn executeSystemUpdate(
		RadixEngineBranch<LedgerAndBFTProof> branch,
		VerifiedVertex vertex,
		long timestamp
	) {
		var systemActions = TxnConstructionRequest.create();
		var view = vertex.getView();
		if (view.compareTo(epochCeilingView) <= 0) {
			systemActions.action(new NextRound(
				view.number(),
				vertex.isTimeout(),
				timestamp,
				getValidatorMapping()
			));
		} else {
			if (vertex.getParentHeader().getView().compareTo(epochCeilingView) < 0) {
				systemActions.action(new NextRound(
					epochCeilingView.number(),
					true,
					timestamp,
					getValidatorMapping()
				));
			}
			systemActions.action(new NextEpoch(timestamp));
		}

		final Txn systemUpdate;
		final RadixEngineResult<LedgerAndBFTProof> result;
		try {
			// TODO: combine construct/execute
			systemUpdate = branch.construct(systemActions).buildWithoutSignature();
			result = branch.execute(List.of(systemUpdate), PermissionLevel.SUPER_USER);
		} catch (RadixEngineException | TxBuilderException e) {
			throw new IllegalStateException(
				String.format("Failed to execute system updates: %s", systemActions), e
			);
		}
		return new RadixEngineTxn(
			systemUpdate,
			result.getProcessedTxn(),
			PermissionLevel.SUPER_USER
		);
	}

	private void executeUserCommands(
		BFTNode proposer,
		RadixEngineBranch<LedgerAndBFTProof> branch,
		List<Txn> nextTxns,
		ImmutableList.Builder<PreparedTxn> successBuilder,
		ImmutableMap.Builder<Txn, Exception> errorBuilder
	) {
		// TODO: This check should probably be done before getting into state computer
		this.maxSigsPerRound.ifPresent(max -> {
			if (nextTxns.size() > max) {
				log.warn("{} proposing {} txns when limit is {}", proposer, nextTxns.size(), max);
			}
		});
		var numToProcess = Integer.min(nextTxns.size(), this.maxSigsPerRound.orElse(Integer.MAX_VALUE));
		for (int i = 0; i < numToProcess; i++) {
			var txn = nextTxns.get(i);
			final RadixEngineResult<LedgerAndBFTProof> result;
			try {
				result = branch.execute(List.of(txn));
			} catch (RadixEngineException e) {
				errorBuilder.put(txn, e);
				invalidProposedCommandEventDispatcher.dispatch(InvalidProposedTxn.create(proposer.getKey(), txn, e));
				return;
			}

			var radixEngineCommand = new RadixEngineTxn(txn, result.getProcessedTxn(), PermissionLevel.USER);
			successBuilder.add(radixEngineCommand);
		}
	}

	@Override
	public StateComputerResult prepare(List<PreparedTxn> previous, VerifiedVertex vertex, long timestamp) {
		var next = vertex.getTxns();
		var transientBranch = this.radixEngine.transientBranch();
		for (PreparedTxn command : previous) {
			// TODO: fix this cast with generics. Currently the fix would become a bit too messy
			final var radixEngineCommand = (RadixEngineTxn) command;
			try {
				transientBranch.execute(
					List.of(radixEngineCommand.txn),
					radixEngineCommand.permissionLevel
				);
			} catch (RadixEngineException e) {
				throw new IllegalStateException("Re-execution of already prepared atom failed: "
					+ radixEngineCommand.processed.getTxn().getId(), e);
			}
		}

		var systemTxn = this.executeSystemUpdate(transientBranch, vertex, timestamp);
		final ImmutableList.Builder<PreparedTxn> successBuilder = ImmutableList.builder();
		successBuilder.add(systemTxn);
		final ImmutableMap.Builder<Txn, Exception> exceptionBuilder = ImmutableMap.builder();
		var nextValidatorSet = systemTxn.processedTxn().getEvents().stream()
			.filter(NextValidatorSetEvent.class::isInstance)
			.map(NextValidatorSetEvent.class::cast)
			.findFirst()
			.map(e -> BFTValidatorSet.from(
				e.nextValidators().stream()
					.map(v -> BFTValidator.from(BFTNode.create(v.getValidatorKey()), v.getAmount())))
			);
		// Don't execute command if changing epochs
		if (nextValidatorSet.isEmpty()) {
			this.executeUserCommands(vertex.getProposer(), transientBranch, next, successBuilder, exceptionBuilder);
		}
		this.radixEngine.deleteBranches();

		return new StateComputerResult(successBuilder.build(), exceptionBuilder.build(), nextValidatorSet.orElse(null));
	}

	private RadixEngineResult<LedgerAndBFTProof> commitInternal(
		VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState
	) {
		var proof = verifiedTxnsAndProof.getProof();

		final RadixEngineResult<LedgerAndBFTProof> result;
		try {
			result = this.radixEngine.execute(
				verifiedTxnsAndProof.getTxns(),
				LedgerAndBFTProof.create(proof, vertexStoreState),
				PermissionLevel.SUPER_USER
			);
		} catch (RadixEngineException e) {
			throw new CommittedBadTxnException(verifiedTxnsAndProof, e);
		} catch (MetadataException e) {
			throw new ByzantineQuorumException(e.getMessage());
		}

		result.getMetadata().getNextForkHash().ifPresent(nextForkHash -> {
			final var nextForkConfig = forks.getByHash(nextForkHash).orElseThrow(); // guaranteed to be present
			log.info("Epoch {} forking RadixEngine to {}", proof.getEpoch() + 1, nextForkConfig.name());
			final var rules = nextForkConfig.engineRules();
			this.radixEngine.replaceConstraintMachine(
				rules.getConstraintMachineConfig(),
				rules.getSerialization(),
				rules.getActionConstructors(),
				rules.getBatchVerifier(),
				rules.getParser()
			);
			this.epochCeilingView = rules.getMaxRounds();
			this.maxSigsPerRound = rules.getMaxSigsPerRound();
		});

		result.getProcessedTxns().forEach(t -> {
			if (t.isSystemOnly()) {
				systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS);
			} else {
				systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_USER_TRANSACTIONS);
			}
		});

		return result;
	}

	@Override
	public void commit(VerifiedTxnsAndProof txnsAndProof, VerifiedVertexStoreState vertexStoreState) {
		final var radixEngineResult = commitInternal(txnsAndProof, vertexStoreState);
		final var txCommitted = radixEngineResult.getProcessedTxns();

		// TODO: refactor mempool to be less generic and make this more efficient
		// TODO: Move this into engine
		List<Txn> removed = this.mempool.committed(txCommitted);
		systemCounters.set(SystemCounters.CounterType.MEMPOOL_COUNT, mempool.getCount());
		if (!removed.isEmpty()) {
			AtomsRemovedFromMempool atomsRemovedFromMempool = AtomsRemovedFromMempool.create(removed);
			mempoolAtomsRemovedEventDispatcher.dispatch(atomsRemovedFromMempool);
		}

		var epochChangeOptional = txnsAndProof.getProof().getNextValidatorSet().map(validatorSet -> {
			var header = txnsAndProof.getProof();
			// TODO: Move vertex stuff somewhere else
			var genesisVertex = UnverifiedVertex.createGenesis(header.getRaw());
			var verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
			var nextLedgerHeader = LedgerHeader.create(
				header.getEpoch() + 1,
				View.genesis(),
				header.getAccumulatorState(),
				header.timestamp()
			);
			var genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
			final var initialState =
				VerifiedVertexStoreState.create(
					HighQC.from(genesisQC),
					verifiedGenesisVertex,
					Optional.empty(),
					hasher
				);
			var proposerElection = new WeightedRotatingLeaders(validatorSet);
			var bftConfiguration = new BFTConfiguration(proposerElection, validatorSet, initialState);
			return new EpochChange(header, bftConfiguration);
		});
		var outputBuilder = ImmutableClassToInstanceMap.builder();
		epochChangeOptional.ifPresent(e -> {
			this.proposerElection = e.getBFTConfiguration().getProposerElection();
			outputBuilder.put(EpochChange.class, e);
		});
		outputBuilder.put(REOutput.class, REOutput.create(txCommitted));
		outputBuilder.put(LedgerAndBFTProof.class, radixEngineResult.getMetadata());
		var ledgerUpdate = new LedgerUpdate(txnsAndProof, outputBuilder.build());
		ledgerUpdateDispatcher.dispatch(ledgerUpdate);
	}
}
