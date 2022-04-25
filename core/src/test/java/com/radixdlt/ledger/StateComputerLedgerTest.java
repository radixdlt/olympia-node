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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.StateComputerLedger.PreparedTxn;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.utils.TypedMocks;
import com.radixdlt.utils.UInt256;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class StateComputerLedgerTest {

  private Mempool mempool;
  private StateComputer stateComputer;
  private StateComputerLedger sut;
  private LedgerProof currentLedgerHeader;
  private SystemCounters counters;
  private Comparator<LedgerProof> headerComparator;
  private LedgerAccumulator accumulator;
  private LedgerAccumulatorVerifier accumulatorVerifier;

  private LedgerHeader ledgerHeader;
  private UnverifiedVertex genesis;
  private VerifiedVertex genesisVertex;
  private QuorumCertificate genesisQC;

  private final Txn nextTxn = Txn.create(new byte[] {0});
  private final Hasher hasher = Sha256Hasher.withDefaultSerialization();
  private final PreparedTxn successfulNextCommand =
      new PreparedTxn() {
        @Override
        public Txn txn() {
          return nextTxn;
        }
      };

  private final long genesisEpoch = 3L;
  private final long genesisStateVersion = 123L;

  @Before
  public void setup() {
    this.mempool = TypedMocks.rmock(Mempool.class);
    // No type check issues with mocking generic here
    this.stateComputer = mock(StateComputer.class);
    this.counters = mock(SystemCounters.class);
    this.headerComparator = TypedMocks.rmock(Comparator.class);

    this.accumulator = new SimpleLedgerAccumulatorAndVerifier(hasher);
    this.accumulatorVerifier = new SimpleLedgerAccumulatorAndVerifier(hasher);

    var accumulatorState = new AccumulatorState(0, HashUtils.zero256());
    this.ledgerHeader = LedgerHeader.genesis(accumulatorState, null, 0);
    this.genesis = UnverifiedVertex.createGenesis(ledgerHeader);
    this.genesisVertex = new VerifiedVertex(genesis, hasher.hash(genesis));
    this.genesisQC = QuorumCertificate.ofGenesis(genesisVertex, ledgerHeader);
    this.currentLedgerHeader =
        this.genesisQC.getCommittedAndLedgerStateProof(hasher).map(Pair::getSecond).orElseThrow();

    this.sut =
        new StateComputerLedger(
            mock(TimeSupplier.class),
            currentLedgerHeader,
            headerComparator,
            stateComputer,
            accumulator,
            accumulatorVerifier,
            counters);
  }

  public void genesisIsEndOfEpoch(boolean endOfEpoch) {
    this.ledgerHeader =
        LedgerHeader.create(
            genesisEpoch,
            View.of(5),
            new AccumulatorState(genesisStateVersion, HashUtils.zero256()),
            12345,
            endOfEpoch
                ? BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE)))
                : null);
    this.genesis = UnverifiedVertex.createGenesis(ledgerHeader);
    this.genesisVertex = new VerifiedVertex(genesis, hasher.hash(genesis));
    this.genesisQC = QuorumCertificate.ofGenesis(genesisVertex, ledgerHeader);
    this.currentLedgerHeader =
        this.genesisQC.getCommittedAndLedgerStateProof(hasher).map(Pair::getSecond).orElseThrow();

    this.sut =
        new StateComputerLedger(
            mock(TimeSupplier.class),
            currentLedgerHeader,
            headerComparator,
            stateComputer,
            accumulator,
            accumulatorVerifier,
            counters);
  }

  @Test
  public void should_not_change_accumulator_when_there_is_no_command() {
    // Arrange
    genesisIsEndOfEpoch(false);
    when(stateComputer.prepare(any(), any(), anyLong()))
        .thenReturn(new StateComputerResult(ImmutableList.of(), ImmutableMap.of()));
    var unverifiedVertex =
        UnverifiedVertex.create(genesisQC, View.of(1), List.of(), BFTNode.random());
    var proposedVertex = new VerifiedVertex(unverifiedVertex, hasher.hash(unverifiedVertex));

    // Act
    Optional<PreparedVertex> nextPrepared = sut.prepare(new LinkedList<>(), proposedVertex);

    // Assert
    assertThat(nextPrepared)
        .hasValueSatisfying(x -> assertThat(x.getLedgerHeader().isEndOfEpoch()).isFalse());
    assertThat(nextPrepared)
        .hasValueSatisfying(
            x ->
                assertThat(x.getLedgerHeader().getAccumulatorState())
                    .isEqualTo(ledgerHeader.getAccumulatorState()));
  }

  @Test
  public void should_not_change_header_when_past_end_of_epoch_even_with_command() {
    // Arrange
    genesisIsEndOfEpoch(true);
    when(stateComputer.prepare(any(), any(), anyLong()))
        .thenReturn(
            new StateComputerResult(ImmutableList.of(successfulNextCommand), ImmutableMap.of()));
    var unverifiedVertex =
        UnverifiedVertex.create(genesisQC, View.of(1), List.of(nextTxn), BFTNode.random());
    var proposedVertex = new VerifiedVertex(unverifiedVertex, hasher.hash(unverifiedVertex));

    // Act
    Optional<PreparedVertex> nextPrepared = sut.prepare(new LinkedList<>(), proposedVertex);

    // Assert
    assertThat(nextPrepared)
        .hasValueSatisfying(x -> assertThat(x.getLedgerHeader().isEndOfEpoch()).isTrue());
    assertThat(nextPrepared)
        .hasValueSatisfying(
            x ->
                assertThat(x.getLedgerHeader().getAccumulatorState())
                    .isEqualTo(ledgerHeader.getAccumulatorState()));
  }

  @Test
  public void should_accumulate_when_next_command_valid() {
    // Arrange
    genesisIsEndOfEpoch(false);
    when(stateComputer.prepare(any(), any(), anyLong()))
        .thenReturn(
            new StateComputerResult(ImmutableList.of(successfulNextCommand), ImmutableMap.of()));

    // Act
    var unverifiedVertex =
        UnverifiedVertex.create(genesisQC, View.of(1), List.of(nextTxn), BFTNode.random());
    var proposedVertex = new VerifiedVertex(unverifiedVertex, hasher.hash(unverifiedVertex));
    Optional<PreparedVertex> nextPrepared = sut.prepare(new LinkedList<>(), proposedVertex);

    // Assert
    assertThat(nextPrepared)
        .hasValueSatisfying(x -> assertThat(x.getLedgerHeader().isEndOfEpoch()).isFalse());
    assertThat(
            nextPrepared.flatMap(
                x ->
                    accumulatorVerifier.verifyAndGetExtension(
                        ledgerHeader.getAccumulatorState(),
                        List.of(nextTxn),
                        txn -> txn.getId().asHashCode(),
                        x.getLedgerHeader().getAccumulatorState())))
        .contains(List.of(nextTxn));
  }

  @Test
  public void should_do_nothing_if_committing_lower_state_version() {
    // Arrange
    genesisIsEndOfEpoch(false);
    when(stateComputer.prepare(any(), any(), anyLong()))
        .thenReturn(
            new StateComputerResult(ImmutableList.of(successfulNextCommand), ImmutableMap.of()));
    final AccumulatorState accumulatorState =
        new AccumulatorState(genesisStateVersion - 1, HashUtils.zero256());
    final LedgerHeader ledgerHeader =
        LedgerHeader.create(genesisEpoch, View.of(2), accumulatorState, 1234);
    final LedgerProof header =
        new LedgerProof(HashUtils.random256(), ledgerHeader, new TimestampedECDSASignatures());
    var verified = VerifiedTxnsAndProof.create(List.of(nextTxn), header);

    // Act
    sut.syncEventProcessor().process(verified);

    // Assert
    verify(stateComputer, never()).commit(any(), any());
    verify(mempool, never()).committed(any());
  }
}
