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
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.ByzantineQuorumException;
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
import com.radixdlt.utils.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer {
	private static final Logger log = LogManager.getLogger();

	private final RadixEngineMempool mempool;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final int maxTxnsPerProposal;
	private final EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher;
	private final EventDispatcher<MempoolAddSuccess> mempoolAddSuccessEventDispatcher;
	private final EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher;
	private final EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher;
	private final EventDispatcher<InvalidProposedTxn> invalidProposedCommandEventDispatcher;
	private final EventDispatcher<TxnsCommittedToLedger> committedDispatcher;
	private final SystemCounters systemCounters;
	private final Hasher hasher;
	private final Forks forks;

	private EpochChange epochChange;
	private View epochCeilingView;

	@Inject
	public RadixEngineStateComputer(
		EpochChange epochChange, // TODO: Should be able to load this directly from state
		RadixEngine<LedgerAndBFTProof> radixEngine,
		Forks forks,
		RadixEngineMempool mempool, // TODO: Move this into radixEngine
		@EpochCeilingView View epochCeilingView, // TODO: Move this into radixEngine
		@MaxTxnsPerProposal int maxTxnsPerProposal, // TODO: Move this into radixEngine
		EventDispatcher<MempoolAddSuccess> mempoolAddedCommandEventDispatcher,
		EventDispatcher<MempoolAddFailure> mempoolAddFailureEventDispatcher,
		EventDispatcher<InvalidProposedTxn> invalidProposedCommandEventDispatcher,
		EventDispatcher<AtomsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher,
		EventDispatcher<TxnsCommittedToLedger> committedDispatcher,
		EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher,
		Hasher hasher,
		SystemCounters systemCounters
	) {
		if (epochCeilingView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.forks = forks;
		this.epochCeilingView = epochCeilingView;
		this.maxTxnsPerProposal = maxTxnsPerProposal;
		this.mempool = Objects.requireNonNull(mempool);
		this.mempoolAddSuccessEventDispatcher = Objects.requireNonNull(mempoolAddedCommandEventDispatcher);
		this.mempoolAddFailureEventDispatcher = Objects.requireNonNull(mempoolAddFailureEventDispatcher);
		this.invalidProposedCommandEventDispatcher = Objects.requireNonNull(invalidProposedCommandEventDispatcher);
		this.mempoolAtomsRemovedEventDispatcher = Objects.requireNonNull(mempoolAtomsRemovedEventDispatcher);
		this.committedDispatcher = Objects.requireNonNull(committedDispatcher);
		this.ledgerUpdateDispatcher = Objects.requireNonNull(ledgerUpdateDispatcher);
		this.hasher = Objects.requireNonNull(hasher);
		this.systemCounters = Objects.requireNonNull(systemCounters);
		this.epochChange = epochChange;
	}

	public static class RadixEngineTxn implements PreparedTxn {
		private final Txn txn;
		private final REProcessedTxn transaction;
		private final PermissionLevel permissionLevel;

		public RadixEngineTxn(
			Txn txn,
			REProcessedTxn transaction,
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
			.map(c -> c.transaction)
			.collect(Collectors.toList());

		// TODO: only return commands which will not cause a missing dependency error
		final List<Txn> txns = mempool.getTxns(maxTxnsPerProposal, cmds);
		systemCounters.add(SystemCounters.CounterType.MEMPOOL_PROPOSED_TRANSACTION, txns.size());
		return txns;
	}

	private LongFunction<ECPublicKey> getValidatorMapping() {
		return l -> epochChange.getBFTConfiguration().getProposerElection().getProposer(View.of(l)).getKey();
	}

	private BFTValidatorSet executeSystemUpdate(
		RadixEngineBranch<LedgerAndBFTProof> branch,
		VerifiedVertex vertex,
		long timestamp,
		ImmutableList.Builder<PreparedTxn> successBuilder
	) {
		// TODO: use this vertex to update per view timeout
		if (vertex.isTimeout()) {
			return null;
		}

		var view = vertex.getView();
		final TxAction systemAction;
		var nextValidatorSet = new AtomicReference<BFTValidatorSet>();
		if (view.compareTo(epochCeilingView) < 0) {
			systemAction = new SystemNextView(
				view.number(),
				timestamp,
				getValidatorMapping()
			);
		} else {
			var stakedValidators = branch.getComputedState(StakedValidators.class);
			if (stakedValidators.toValidatorSet() == null) {
				// FIXME: Better way to handle rare case when there isn't enough in validator set
				systemAction = new SystemNextView(
					view.number(),
					timestamp,
					getValidatorMapping()
				);
			} else {
				systemAction = new SystemNextEpoch(updates -> {
					var cur = stakedValidators;
					for (var u : updates) {
						cur = cur.setStake(u.getValidatorKey(), u.getAmount());
					}
					// FIXME: cur.toValidatorSet() may be null
					var validatorSet = cur.toValidatorSet();
					if (validatorSet == null) {
						throw new IllegalStateException();
					}
					nextValidatorSet.set(validatorSet);
					return validatorSet.nodes().stream()
						.map(BFTNode::getKey)
						.sorted(Comparator.comparing(ECPublicKey::getBytes, Arrays::compare))
						.collect(Collectors.toList());
				}, timestamp);
			}
		}

		final Txn systemUpdate;
		final List<REProcessedTxn> txs;
		try {
			// TODO: combine construct/execute
			systemUpdate = branch.construct(systemAction).buildWithoutSignature();
			txs = branch.execute(List.of(systemUpdate), PermissionLevel.SUPER_USER);
		} catch (RadixEngineException | TxBuilderException e) {
			throw new IllegalStateException(
				String.format("Failed to execute system update: %s", systemAction), e
			);
		}
		RadixEngineTxn radixEngineCommand = new RadixEngineTxn(
			systemUpdate,
			txs.get(0),
			PermissionLevel.SUPER_USER
		);
		successBuilder.add(radixEngineCommand);

		return nextValidatorSet.get();
	}

	private void executeUserCommands(
		RadixEngineBranch<LedgerAndBFTProof> branch,
		List<Txn> nextTxns,
		ImmutableList.Builder<PreparedTxn> successBuilder,
		ImmutableMap.Builder<Txn, Exception> errorBuilder
	) {
		nextTxns.forEach(txn -> {
			final List<REProcessedTxn> parsed;
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
					+ radixEngineCommand.transaction.getTxn().getId(), e);
			}
		}

		final ImmutableList.Builder<PreparedTxn> successBuilder = ImmutableList.builder();
		final ImmutableMap.Builder<Txn, Exception> exceptionBuilder = ImmutableMap.builder();
		final BFTValidatorSet validatorSet = this.executeSystemUpdate(transientBranch, vertex, timestamp, successBuilder);
		// Don't execute command if changing epochs
		if (validatorSet == null) {
			this.executeUserCommands(transientBranch, next, successBuilder, exceptionBuilder);
		}
		this.radixEngine.deleteBranches();

		return new StateComputerResult(successBuilder.build(), exceptionBuilder.build(), validatorSet);
	}

	private List<REProcessedTxn> commitInternal(
		VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState
	) {
		var proof = verifiedTxnsAndProof.getProof();
		var ledgerAndBFTProof = LedgerAndBFTProof.create(proof, vertexStoreState);

		final List<REProcessedTxn> radixEngineTxns;
		try {
			radixEngineTxns = this.radixEngine.execute(
				verifiedTxnsAndProof.getTxns(),
				ledgerAndBFTProof,
				PermissionLevel.SUPER_USER
			);
		} catch (RadixEngineException e) {
			throw new ByzantineQuorumException(
				String.format(
					"Trying to commit bad txnId: %s payload: %s",
					e.getTxn().getId(),
					Bytes.toHexString(e.getTxn().getPayload())
				),
				e
			);
		}

		// Next epoch
		if (proof.getNextValidatorSet().isPresent()) {
			forks.ifForkGet(proof.getEpoch() + 1)
				.ifPresent(rules -> {
					log.info("Epoch {} Forking RadixEngine to {}", proof.getEpoch() + 1, rules.name());
					this.radixEngine.replaceConstraintMachine(
						rules.getConstraintMachineConfig(),
						rules.getSerialization(),
						rules.getActionConstructors(),
						rules.getBatchVerifier(),
						rules.getParser(),
						rules.getPostProcessedVerifier()
					);
					this.epochCeilingView = rules.getMaxRounds();
				});
		}

		radixEngineTxns.forEach(t -> {
			if (t.isSystemOnly()) {
				systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS);
			} else {
				systemCounters.increment(SystemCounters.CounterType.RADIX_ENGINE_USER_TRANSACTIONS);
			}
		});

		return radixEngineTxns;
	}

	@Override
	public void commit(VerifiedTxnsAndProof txnsAndProof, VerifiedVertexStoreState vertexStoreState) {
		var txCommitted = commitInternal(txnsAndProof, vertexStoreState);

		// TODO: refactor mempool to be less generic and make this more efficient
		// TODO: Move this into engine
		List<Txn> removed = this.mempool.committed(txCommitted);
		systemCounters.set(SystemCounters.CounterType.MEMPOOL_COUNT, mempool.getCount());
		if (!removed.isEmpty()) {
			AtomsRemovedFromMempool atomsRemovedFromMempool = AtomsRemovedFromMempool.create(removed);
			mempoolAtomsRemovedEventDispatcher.dispatch(atomsRemovedFromMempool);
		}

		committedDispatcher.dispatch(TxnsCommittedToLedger.create(txCommitted));

		Optional<EpochChange> epochChangeOptional = txnsAndProof.getProof().getNextValidatorSet().map(validatorSet -> {
			LedgerProof header = txnsAndProof.getProof();
			UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(header.getRaw());
			VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
			LedgerHeader nextLedgerHeader = LedgerHeader.create(
				header.getEpoch() + 1,
				View.genesis(),
				header.getAccumulatorState(),
				header.timestamp()
			);
			QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
			final var initialState =
				VerifiedVertexStoreState.create(
					HighQC.from(genesisQC),
					verifiedGenesisVertex,
					Optional.empty(),
					hasher
				);
			var proposerElection = new WeightedRotatingLeaders(validatorSet, Comparator.comparing(v -> v.getNode().getKey().euid()));
			var bftConfiguration = new BFTConfiguration(proposerElection, validatorSet, initialState);
			return new EpochChange(header, bftConfiguration);
		});

		epochChangeOptional.ifPresent(e -> this.epochChange = e);

		var ledgerUpdate = new LedgerUpdate(txnsAndProof, epochChangeOptional);
		ledgerUpdateDispatcher.dispatch(ledgerUpdate);
	}
}
