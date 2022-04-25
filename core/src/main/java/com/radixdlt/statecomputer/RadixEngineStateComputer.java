/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.statecomputer;

import static com.radixdlt.atom.TxAction.*;
import static com.radixdlt.monitoring.SystemCounters.*;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.radixdlt.atom.*;
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
import com.radixdlt.constraintmachine.REEvent;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.PostProcessorException;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.RadixEngineResult;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.ByzantineQuorumException;
import com.radixdlt.ledger.CommittedBadTxnException;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.StateComputerLedger.PreparedTxn;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.statecomputer.forks.Forks;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongFunction;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Wraps the Radix Engine and emits messages based on success or failure */
public final class RadixEngineStateComputer implements StateComputer {
  private static final Logger log = LogManager.getLogger();

  private final RadixEngineMempool mempool;
  private final RadixEngine<LedgerAndBFTProof> radixEngine;
  private final EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher;
  private final EventDispatcher<MempoolAddSuccess> mempoolAddSuccessEventDispatcher;
  private final EventDispatcher<TxnsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher;
  private final EventDispatcher<InvalidProposedTxn> invalidProposedCommandEventDispatcher;
  private final SystemCounters systemCounters;
  private final Hasher hasher;
  private final Forks forks;
  private final Object lock = new Object();

  private ProposerElection proposerElection;
  private View epochCeilingView;
  private OptionalInt maxSigsPerRound;

  @Inject
  public RadixEngineStateComputer(
      ProposerElection proposerElection, // TODO: Should be able to load this directly from state
      RadixEngine<LedgerAndBFTProof> radixEngine,
      Forks forks,
      RadixEngineMempool mempool, // TODO: Move this into radixEngine
      @EpochCeilingView View epochCeilingView, // TODO: Move this into radixEngine
      @MaxSigsPerRound OptionalInt maxSigsPerRound, // TODO: Move this into radixEngine
      EventDispatcher<MempoolAddSuccess> mempoolAddedCommandEventDispatcher,
      EventDispatcher<InvalidProposedTxn> invalidProposedCommandEventDispatcher,
      EventDispatcher<TxnsRemovedFromMempool> mempoolAtomsRemovedEventDispatcher,
      EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher,
      Hasher hasher,
      SystemCounters systemCounters) {
    if (epochCeilingView.isGenesis()) {
      throw new IllegalArgumentException("Epoch change view must not be genesis.");
    }

    this.radixEngine = Objects.requireNonNull(radixEngine);
    this.forks = Objects.requireNonNull(forks);
    this.epochCeilingView = epochCeilingView;
    this.maxSigsPerRound = maxSigsPerRound;
    this.mempool = Objects.requireNonNull(mempool);
    this.mempoolAddSuccessEventDispatcher =
        Objects.requireNonNull(mempoolAddedCommandEventDispatcher);
    this.invalidProposedCommandEventDispatcher =
        Objects.requireNonNull(invalidProposedCommandEventDispatcher);
    this.mempoolAtomsRemovedEventDispatcher =
        Objects.requireNonNull(mempoolAtomsRemovedEventDispatcher);
    this.ledgerUpdateDispatcher = Objects.requireNonNull(ledgerUpdateDispatcher);
    this.hasher = Objects.requireNonNull(hasher);
    this.systemCounters = Objects.requireNonNull(systemCounters);
    this.proposerElection = proposerElection;
  }

  public record RadixEngineTxn(Txn txn, REProcessedTxn processed, PermissionLevel permissionLevel)
      implements PreparedTxn {}

  public REProcessedTxn test(byte[] payload, boolean isSigned) throws RadixEngineException {
    synchronized (lock) {
      var txn =
          isSigned
              ? Txn.create(payload)
              : TxLowLevelBuilder.newBuilder(payload).sig(ECDSASignature.zeroSignature()).build();

      var checker = radixEngine.transientBranch();

      try {
        return checker.execute(List.of(txn), !isSigned).getProcessedTxn();
      } finally {
        radixEngine.deleteBranches();
      }
    }
  }

  public REProcessedTxn addToMempool(Txn txn) throws MempoolRejectedException {
    return addToMempool(txn, null);
  }

  public REProcessedTxn addToMempool(Txn txn, BFTNode origin) throws MempoolRejectedException {
    synchronized (lock) {
      try {
        var processed = mempool.add(txn);

        systemCounters.increment(CounterType.MEMPOOL_ADD_SUCCESS);
        systemCounters.set(CounterType.MEMPOOL_CURRENT_SIZE, mempool.getCount());

        var success = MempoolAddSuccess.create(txn, processed, origin);
        mempoolAddSuccessEventDispatcher.dispatch(success);

        return processed;
      } catch (MempoolDuplicateException e) {
        throw e;
      } catch (MempoolRejectedException e) {
        systemCounters.increment(CounterType.MEMPOOL_ADD_FAILURE);
        throw e;
      }
    }
  }

  @Override
  public void addToMempool(MempoolAdd mempoolAdd, @Nullable BFTNode origin) {
    mempoolAdd
        .txns()
        .forEach(
            txn -> {
              try {
                addToMempool(txn, origin);
              } catch (MempoolDuplicateException ex) {
                log.trace(
                    "Transaction {} was not added as it was already in the mempool", txn.getId());
              } catch (MempoolRejectedException ex) {
                log.debug("Transaction {} was not added to the mempool", txn.getId(), ex);
              }
            });
  }

  @Override
  public List<Txn> getNextTxnsFromMempool(List<PreparedTxn> prepared) {
    synchronized (lock) {
      var cmds =
          prepared.stream().map(RadixEngineTxn.class::cast).map(RadixEngineTxn::processed).toList();

      // TODO: only return commands which will not cause a missing dependency error
      return mempool.getTxns(maxSigsPerRound.orElse(50), cmds);
    }
  }

  private LongFunction<ECPublicKey> getValidatorMapping() {
    return l -> proposerElection.getProposer(View.of(l)).getKey();
  }

  private RadixEngineTxn executeSystemUpdate(
      RadixEngineBranch<LedgerAndBFTProof> branch, VerifiedVertex vertex, long timestamp) {
    var systemActions = TxnConstructionRequest.create();
    var view = vertex.getView();
    if (view.compareTo(epochCeilingView) <= 0) {
      systemActions.action(
          new NextRound(view.number(), vertex.isTimeout(), timestamp, getValidatorMapping()));
    } else {
      if (vertex.getParentHeader().getView().compareTo(epochCeilingView) < 0) {
        systemActions.action(
            new NextRound(epochCeilingView.number(), true, timestamp, getValidatorMapping()));
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
          String.format("Failed to execute system updates: %s", systemActions), e);
    }
    return new RadixEngineTxn(systemUpdate, result.getProcessedTxn(), PermissionLevel.SUPER_USER);
  }

  private void executeUserCommands(
      BFTNode proposer,
      RadixEngineBranch<LedgerAndBFTProof> branch,
      List<Txn> nextTxns,
      ImmutableList.Builder<PreparedTxn> successBuilder,
      ImmutableMap.Builder<Txn, Exception> errorBuilder) {
    // TODO: This check should probably be done before getting into state computer
    this.maxSigsPerRound.ifPresent(
        max -> {
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
        invalidProposedCommandEventDispatcher.dispatch(
            InvalidProposedTxn.create(proposer.getKey(), txn, e));
        return;
      }

      var radixEngineCommand =
          new RadixEngineTxn(txn, result.getProcessedTxn(), PermissionLevel.USER);
      successBuilder.add(radixEngineCommand);
    }
  }

  @Override
  public StateComputerResult prepare(
      List<PreparedTxn> previous, VerifiedVertex vertex, long timestamp) {
    synchronized (lock) {
      var next = vertex.getTxns();
      var transientBranch = this.radixEngine.transientBranch();

      for (var command : previous) {
        // TODO: fix this cast with generics. Currently the fix would become a bit too messy
        final var radixEngineCommand = (RadixEngineTxn) command;
        try {
          transientBranch.execute(
              List.of(radixEngineCommand.txn()), radixEngineCommand.permissionLevel());
        } catch (RadixEngineException e) {
          throw new IllegalStateException(
              "Re-execution of already prepared transaction failed: "
                  + radixEngineCommand.processed.getTxn().getId(),
              e);
        }
      }

      var systemTxn = this.executeSystemUpdate(transientBranch, vertex, timestamp);
      var successBuilder = ImmutableList.<PreparedTxn>builder();

      successBuilder.add(systemTxn);

      var exceptionBuilder = ImmutableMap.<Txn, Exception>builder();
      var nextValidatorSet =
          systemTxn.processed().getEvents().stream()
              .filter(REEvent.NextValidatorSetEvent.class::isInstance)
              .map(REEvent.NextValidatorSetEvent.class::cast)
              .findFirst()
              .map(
                  e ->
                      BFTValidatorSet.from(
                          e.nextValidators().stream()
                              .map(
                                  v ->
                                      BFTValidator.from(
                                          BFTNode.create(v.validatorKey()), v.amount()))));
      // Don't execute command if changing epochs
      if (nextValidatorSet.isEmpty()) {
        this.executeUserCommands(
            vertex.getProposer(), transientBranch, next, successBuilder, exceptionBuilder);
      }
      this.radixEngine.deleteBranches();

      return new StateComputerResult(
          successBuilder.build(), exceptionBuilder.build(), nextValidatorSet.orElse(null));
    }
  }

  private RadixEngineResult<LedgerAndBFTProof> commitInternal(
      VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState) {
    var proof = verifiedTxnsAndProof.getProof();

    final RadixEngineResult<LedgerAndBFTProof> result;
    try {
      result =
          this.radixEngine.execute(
              verifiedTxnsAndProof.getTxns(),
              LedgerAndBFTProof.create(proof, vertexStoreState),
              PermissionLevel.SUPER_USER);
    } catch (RadixEngineException e) {
      throw new CommittedBadTxnException(verifiedTxnsAndProof, e);
    } catch (PostProcessorException e) {
      throw new ByzantineQuorumException(e.getMessage(), e);
    }

    result.getMetadata().getNextForkName().ifPresent(this::forkRadixEngine);

    result
        .getProcessedTxns()
        .forEach(
            t ->
                systemCounters.increment(
                    t.isSystemOnly()
                        ? CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS
                        : CounterType.RADIX_ENGINE_USER_TRANSACTIONS));

    return result;
  }

  private void forkRadixEngine(String nextForkName) {
    final var nextForkConfig =
        forks.getByName(nextForkName).orElseThrow(); // guaranteed to be present
    if (log.isInfoEnabled()) {
      log.info("Forking RadixEngine to {}", nextForkConfig.name());
    }
    final var rules = nextForkConfig.engineRules();
    this.radixEngine.replaceConstraintMachine(
        rules.constraintMachineConfig(),
        rules.serialization(),
        rules.actionConstructors(),
        rules.postProcessor(),
        rules.parser());
    this.epochCeilingView = rules.maxRounds();
    this.maxSigsPerRound = rules.maxSigsPerRound();
  }

  @Override
  public void commit(VerifiedTxnsAndProof txnsAndProof, VerifiedVertexStoreState vertexStoreState) {
    synchronized (lock) {
      final var radixEngineResult = commitInternal(txnsAndProof, vertexStoreState);
      final var txCommitted = radixEngineResult.getProcessedTxns();

      // TODO: refactor mempool to be less generic and make this more efficient
      // TODO: Move this into engine
      var removed = this.mempool.committed(txCommitted);
      systemCounters.set(CounterType.MEMPOOL_CURRENT_SIZE, mempool.getCount());
      if (!removed.isEmpty()) {
        var atomsRemovedFromMempool = TxnsRemovedFromMempool.create(removed);
        mempoolAtomsRemovedEventDispatcher.dispatch(atomsRemovedFromMempool);
      }

      var epochChangeOptional =
          txnsAndProof
              .getProof()
              .getNextValidatorSet()
              .map(
                  validatorSet -> {
                    var header = txnsAndProof.getProof();
                    // TODO: Move vertex stuff somewhere else
                    var genesisVertex = UnverifiedVertex.createGenesis(header.getRaw());
                    var verifiedGenesisVertex =
                        new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
                    var nextLedgerHeader =
                        LedgerHeader.create(
                            header.getEpoch() + 1,
                            View.genesis(),
                            header.getAccumulatorState(),
                            header.timestamp());
                    var genesisQC =
                        QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
                    final var initialState =
                        VerifiedVertexStoreState.create(
                            HighQC.from(genesisQC),
                            verifiedGenesisVertex,
                            Optional.empty(),
                            hasher);
                    var proposerElection = new WeightedRotatingLeaders(validatorSet);
                    var bftConfiguration =
                        new BFTConfiguration(proposerElection, validatorSet, initialState);
                    return new EpochChange(header, bftConfiguration);
                  });
      var outputBuilder = ImmutableClassToInstanceMap.builder();
      epochChangeOptional.ifPresent(
          e -> {
            this.proposerElection = e.getBFTConfiguration().getProposerElection();
            outputBuilder.put(EpochChange.class, e);
          });
      outputBuilder.put(REOutput.class, REOutput.create(txCommitted));
      outputBuilder.put(LedgerAndBFTProof.class, radixEngineResult.getMetadata());
      var ledgerUpdate = new LedgerUpdate(txnsAndProof, outputBuilder.build());
      ledgerUpdateDispatcher.dispatch(ledgerUpdate);
    }
  }
}
