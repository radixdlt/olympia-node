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

package com.radixdlt.ledger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.NextTxnsGenerator;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.store.LastProof;
import com.radixdlt.utils.TimeSupplier;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Synchronizes execution */
public final class StateComputerLedger implements Ledger, NextTxnsGenerator {

  public interface PreparedTxn {
    Txn txn();
  }

  public static class StateComputerResult {
    private final List<PreparedTxn> preparedTxns;
    private final Map<Txn, Exception> failedCommands;
    private final BFTValidatorSet nextValidatorSet;

    public StateComputerResult(
        List<PreparedTxn> preparedTxns,
        Map<Txn, Exception> failedCommands,
        BFTValidatorSet nextValidatorSet) {
      this.preparedTxns = Objects.requireNonNull(preparedTxns);
      this.failedCommands = Objects.requireNonNull(failedCommands);
      this.nextValidatorSet = nextValidatorSet;
    }

    public StateComputerResult(List<PreparedTxn> preparedTxns, Map<Txn, Exception> failedCommands) {
      this(preparedTxns, failedCommands, null);
    }

    public Optional<BFTValidatorSet> getNextValidatorSet() {
      return Optional.ofNullable(nextValidatorSet);
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

    void commit(
        VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState);
  }

  private final Comparator<LedgerProof> headerComparator;
  private final StateComputer stateComputer;
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
      LedgerAccumulator accumulator,
      LedgerAccumulatorVerifier verifier,
      SystemCounters counters) {
    this.timeSupplier = Objects.requireNonNull(timeSupplier);
    this.headerComparator = Objects.requireNonNull(headerComparator);
    this.stateComputer = Objects.requireNonNull(stateComputer);
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
    final ImmutableList<PreparedTxn> preparedTxns =
        prepared.stream()
            .flatMap(PreparedVertex::successfulCommands)
            .collect(ImmutableList.toImmutableList());
    synchronized (lock) {
      return stateComputer.getNextTxnsFromMempool(preparedTxns);
    }
  }

  @Override
  public Optional<PreparedVertex> prepare(
      LinkedList<PreparedVertex> previous, VerifiedVertex vertex) {
    final LedgerHeader parentHeader = vertex.getParentHeader().getLedgerHeader();
    final AccumulatorState parentAccumulatorState = parentHeader.getAccumulatorState();
    final ImmutableList<PreparedTxn> prevCommands =
        previous.stream()
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
        final PreparedVertex preparedVertex =
            vertex
                .withHeader(
                    parentHeader.updateViewAndTimestamp(vertex.getView(), quorumTimestamp),
                    localTimestamp)
                .andTxns(ImmutableList.of(), ImmutableMap.of());
        return Optional.of(preparedVertex);
      }

      final var maybeCommands =
          this.verifier.verifyAndGetExtension(
              this.currentLedgerHeader.getAccumulatorState(),
              prevCommands,
              p -> p.txn().getId().asHashCode(),
              parentAccumulatorState);

      // TODO: Write a test to get here
      // Can possibly get here without maliciousness if parent vertex isn't locked by everyone else
      if (maybeCommands.isEmpty()) {
        return Optional.empty();
      }

      final var concatenatedCommands = maybeCommands.get();

      final StateComputerResult result =
          stateComputer.prepare(concatenatedCommands, vertex, quorumTimestamp);

      AccumulatorState accumulatorState = parentHeader.getAccumulatorState();
      for (PreparedTxn txn : result.getSuccessfulCommands()) {
        accumulatorState =
            this.accumulator.accumulate(accumulatorState, txn.txn().getId().asHashCode());
      }

      final LedgerHeader ledgerHeader =
          LedgerHeader.create(
              parentHeader.getEpoch(),
              vertex.getView(),
              accumulatorState,
              quorumTimestamp,
              result.getNextValidatorSet().orElse(null));

      final long localTimestamp = timeSupplier.currentTime();
      return Optional.of(
          vertex
              .withHeader(ledgerHeader, localTimestamp)
              .andTxns(result.getSuccessfulCommands(), result.getFailedCommands()));
    }
  }

  public EventProcessor<BFTCommittedUpdate> bftCommittedUpdateEventProcessor() {
    return committedUpdate -> {
      final ImmutableList<Txn> txns =
          committedUpdate.committed().stream()
              .flatMap(PreparedVertex::successfulCommands)
              .map(PreparedTxn::txn)
              .collect(ImmutableList.toImmutableList());
      var proof = committedUpdate.vertexStoreState().getRootHeader();
      var verifiedTxnsAndProof = VerifiedTxnsAndProof.create(txns, proof);

      // TODO: Make these two atomic (RPNV1-827)
      this.commit(verifiedTxnsAndProof, committedUpdate.vertexStoreState());
    };
  }

  public EventProcessor<VerifiedTxnsAndProof> syncEventProcessor() {
    return p -> this.commit(p, null);
  }

  private void commit(
      VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState) {
    synchronized (lock) {
      final LedgerProof nextHeader = verifiedTxnsAndProof.getProof();
      if (headerComparator.compare(nextHeader, this.currentLedgerHeader) <= 0) {
        return;
      }

      var verifiedExtension =
          verifier.verifyAndGetExtension(
              this.currentLedgerHeader.getAccumulatorState(),
              verifiedTxnsAndProof.getTxns(),
              txn -> txn.getId().asHashCode(),
              verifiedTxnsAndProof.getProof().getAccumulatorState());

      if (verifiedExtension.isEmpty()) {
        throw new ByzantineQuorumException(
            "Accumulator failure " + currentLedgerHeader + " " + verifiedTxnsAndProof);
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
      this.counters.set(
          CounterType.LEDGER_STATE_VERSION, this.currentLedgerHeader.getStateVersion());
    }
  }
}
