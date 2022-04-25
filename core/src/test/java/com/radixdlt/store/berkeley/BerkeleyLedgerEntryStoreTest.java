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

package com.radixdlt.store.berkeley;

import static com.radixdlt.statecomputer.forks.RERulesVersion.OLYMPIA_V1;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.monitoring.SystemCountersImpl;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.ForkVotingResult;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.StoreConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BerkeleyLedgerEntryStoreTest {

  private TemporaryFolder dir;
  private BerkeleyLedgerEntryStore sut;

  @Before
  public void setup() throws IOException {
    dir = new TemporaryFolder();
    dir.create();

    sut =
        new BerkeleyLedgerEntryStore(
            DefaultSerialization.getInstance(),
            new DatabaseEnvironment(
                dir.getRoot().getAbsolutePath(), (long) (Runtime.getRuntime().maxMemory() * 0.125)),
            new StoreConfig(1000),
            new SystemCountersImpl(0L),
            Set.of());
  }

  @Test
  public void test_forks_voting_results() throws RadixEngineException {
    final var threshold = new CandidateForkConfig.Threshold((short) 8000, 10);
    final var candidateFork1 =
        new CandidateForkConfig(
            "fork1",
            OLYMPIA_V1.create(RERulesConfig.testingDefault()),
            ImmutableSet.of(threshold),
            1L,
            5L);
    final var candidateFork2 =
        new CandidateForkConfig(
            "fork2",
            OLYMPIA_V1.create(RERulesConfig.testingDefault()),
            ImmutableSet.of(threshold),
            1L,
            5L);
    final var candidateFork3 =
        new CandidateForkConfig(
            "fork3",
            OLYMPIA_V1.create(RERulesConfig.testingDefault()),
            ImmutableSet.of(threshold),
            1L,
            5L);
    final var candidateFork1Id = CandidateForkVote.candidateForkId(candidateFork1);
    final var candidateFork2Id = CandidateForkVote.candidateForkId(candidateFork2);
    final var candidateFork3Id = CandidateForkVote.candidateForkId(candidateFork3);

    storeMetadataWithForks(
        1,
        ImmutableSet.of(
            new ForkVotingResult(2, candidateFork1Id, (short) 5000),
            new ForkVotingResult(2, candidateFork2Id, (short) 5000)));

    storeMetadataWithForks(
        2,
        ImmutableSet.of(
            new ForkVotingResult(3, candidateFork1Id, (short) 6000),
            new ForkVotingResult(3, candidateFork2Id, (short) 5000)));

    storeMetadataWithForks(
        3,
        ImmutableSet.of(
            new ForkVotingResult(4, candidateFork1Id, (short) 8000),
            new ForkVotingResult(4, candidateFork3Id, (short) 6000)));

    storeMetadataWithForks(
        4,
        ImmutableSet.of(
            new ForkVotingResult(5, candidateFork1Id, (short) 8500),
            new ForkVotingResult(5, candidateFork3Id, (short) 6000)));

    storeMetadataWithForks(
        5,
        ImmutableSet.of(
            new ForkVotingResult(6, candidateFork2Id, (short) 8500),
            new ForkVotingResult(6, candidateFork3Id, (short) 6000)));

    storeMetadataWithForks(
        6,
        ImmutableSet.of(
            new ForkVotingResult(7, candidateFork1Id, (short) 5500),
            new ForkVotingResult(7, candidateFork2Id, (short) 9500),
            new ForkVotingResult(7, candidateFork3Id, (short) 6000)));

    /* epoch >= 0 && epoch < 4; fork1 */
    try (var cursor = sut.forkVotingResultsCursor(0L, 4L, candidateFork1Id)) {
      final var items = cursorToList(cursor);
      assertEquals(2, items.size());
      assertEquals(new ForkVotingResult(2L, candidateFork1Id, (short) 5000), items.get(0));
      assertEquals(new ForkVotingResult(3L, candidateFork1Id, (short) 6000), items.get(1));
    }

    /* epoch >= 3 && epoch < 6; fork1 */
    try (var cursor = sut.forkVotingResultsCursor(3L, 6L, candidateFork1Id)) {
      final var items = cursorToList(cursor);
      assertEquals(3, items.size());
      assertEquals(new ForkVotingResult(3L, candidateFork1Id, (short) 6000), items.get(0));
      assertEquals(new ForkVotingResult(4L, candidateFork1Id, (short) 8000), items.get(1));
      assertEquals(new ForkVotingResult(5L, candidateFork1Id, (short) 8500), items.get(2));
    }

    /* epoch >= 3 && epoch < 8; fork2 */
    try (var cursor = sut.forkVotingResultsCursor(3L, 8L, candidateFork2Id)) {
      final var items = cursorToList(cursor);
      assertEquals(3, items.size());
      assertEquals(new ForkVotingResult(3L, candidateFork2Id, (short) 5000), items.get(0));
      assertEquals(new ForkVotingResult(6L, candidateFork2Id, (short) 8500), items.get(1));
      assertEquals(new ForkVotingResult(7L, candidateFork2Id, (short) 9500), items.get(2));
    }

    // test long max value
    try (var cursor = sut.forkVotingResultsCursor(7L, Long.MAX_VALUE, candidateFork1Id)) {
      final var items = cursorToList(cursor);
      assertEquals(1, items.size());
      assertEquals(new ForkVotingResult(7L, candidateFork1Id, (short) 5500), items.get(0));
    }

    // test inverted params
    try (var cursor = sut.forkVotingResultsCursor(7L, 1L, candidateFork1Id)) {
      final var items = cursorToList(cursor);
      assertEquals(0, items.size());
    }
  }

  private void storeMetadataWithForks(long epoch, ImmutableSet<ForkVotingResult> forkVotingResults)
      throws RadixEngineException {
    final var fakeTx = mock(REProcessedTxn.class);
    final var txn = mock(Txn.class);
    when(txn.getId()).thenReturn(AID.from(HashUtils.random256().asBytes()));
    when(fakeTx.getTxn()).thenReturn(txn);
    when(fakeTx.getGroupedStateUpdates()).thenReturn(List.of());
    when(txn.getPayload()).thenReturn(HashUtils.random256().asBytes());

    final var proof1 =
        LedgerAndBFTProof.create(
                new LedgerProof(
                    HashUtils.random256(),
                    LedgerHeader.create(
                        epoch,
                        View.of(0L),
                        new AccumulatorState(
                            epoch /* using same state version as epoch */,
                            HashCode.fromInt(1) /* unused */),
                        0L),
                    new TimestampedECDSASignatures(Map.of())))
            .withForksVotingResults(forkVotingResults);

    sut.transaction(
        tx -> {
          tx.storeTxn(fakeTx);
          tx.storeMetadata(proof1);
          return null;
        });
  }

  private <T> List<T> cursorToList(CloseableCursor<T> cursor) {
    final var res = new ArrayList<T>();
    while (cursor.hasNext()) {
      res.add(cursor.next());
    }
    return res;
  }
}
