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

package com.radixdlt.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof.OrderByEpochAndVersionComparator;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class LedgerProofTest {
  private OrderByEpochAndVersionComparator headerComparator;

  @Before
  public void setup() {
    this.headerComparator = new OrderByEpochAndVersionComparator();
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(LedgerProof.class)
        .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
        .verify();
  }

  @Test
  public void testGetters() {
    LedgerHeader l0 = mock(LedgerHeader.class);
    HashCode accumulatorHash = mock(HashCode.class);
    View view = mock(View.class);
    when(l0.getEpoch()).thenReturn(3L);
    AccumulatorState accumulatorState = mock(AccumulatorState.class);
    when(accumulatorState.getAccumulatorHash()).thenReturn(accumulatorHash);
    when(accumulatorState.getStateVersion()).thenReturn(12345L);
    when(l0.getAccumulatorState()).thenReturn(accumulatorState);
    when(l0.getView()).thenReturn(view);
    when(l0.timestamp()).thenReturn(2468L);
    when(l0.isEndOfEpoch()).thenReturn(true);
    var ledgerHeaderAndProof =
        new LedgerProof(HashUtils.random256(), l0, mock(TimestampedECDSASignatures.class));
    assertThat(ledgerHeaderAndProof.getEpoch()).isEqualTo(3L);
    assertThat(ledgerHeaderAndProof.getStateVersion()).isEqualTo(12345L);
    assertThat(ledgerHeaderAndProof.getView()).isEqualTo(view);
    assertThat(ledgerHeaderAndProof.timestamp()).isEqualTo(2468L);
    assertThat(ledgerHeaderAndProof.isEndOfEpoch()).isTrue();
  }

  @Test
  public void testComparsionBetweenDifferentEpochs() {
    LedgerHeader l0 = mock(LedgerHeader.class);
    when(l0.getEpoch()).thenReturn(1L);
    var s0 = new LedgerProof(HashUtils.random256(), l0, mock(TimestampedECDSASignatures.class));
    LedgerHeader l1 = mock(LedgerHeader.class);
    when(l1.getEpoch()).thenReturn(2L);
    LedgerProof s1 =
        new LedgerProof(HashUtils.random256(), l1, mock(TimestampedECDSASignatures.class));
    assertThat(headerComparator.compare(s0, s1)).isNegative();
    assertThat(headerComparator.compare(s1, s0)).isPositive();
  }

  @Test
  public void testComparsionBetweenDifferentStateVersions() {
    LedgerHeader l0 = mock(LedgerHeader.class);
    when(l0.getEpoch()).thenReturn(2L);
    AccumulatorState accumulatorState = mock(AccumulatorState.class);
    when(accumulatorState.getStateVersion()).thenReturn(2L);
    when(l0.getAccumulatorState()).thenReturn(accumulatorState);
    LedgerProof s0 =
        new LedgerProof(HashUtils.random256(), l0, mock(TimestampedECDSASignatures.class));
    LedgerHeader l1 = mock(LedgerHeader.class);
    when(l1.getEpoch()).thenReturn(2L);
    AccumulatorState accumulatorState1 = mock(AccumulatorState.class);
    when(accumulatorState1.getStateVersion()).thenReturn(3L);
    when(l1.getAccumulatorState()).thenReturn(accumulatorState1);
    LedgerProof s1 =
        new LedgerProof(HashUtils.random256(), l1, mock(TimestampedECDSASignatures.class));
    assertThat(headerComparator.compare(s0, s1)).isNegative();
    assertThat(headerComparator.compare(s1, s0)).isPositive();
  }

  @Test
  public void testComparsionWithEndOfEpoch() {
    LedgerHeader l0 = mock(LedgerHeader.class);
    when(l0.getEpoch()).thenReturn(2L);
    AccumulatorState accumulatorState = mock(AccumulatorState.class);
    when(accumulatorState.getStateVersion()).thenReturn(2L);
    when(l0.getAccumulatorState()).thenReturn(accumulatorState);
    when(l0.isEndOfEpoch()).thenReturn(false);
    LedgerProof s0 =
        new LedgerProof(HashUtils.random256(), l0, mock(TimestampedECDSASignatures.class));
    LedgerHeader l1 = mock(LedgerHeader.class);
    when(l1.getEpoch()).thenReturn(2L);
    AccumulatorState accumulatorState1 = mock(AccumulatorState.class);
    when(accumulatorState1.getStateVersion()).thenReturn(3L);
    when(l1.getAccumulatorState()).thenReturn(accumulatorState1);
    when(l1.isEndOfEpoch()).thenReturn(true);
    LedgerProof s1 =
        new LedgerProof(HashUtils.random256(), l1, mock(TimestampedECDSASignatures.class));
    assertThat(headerComparator.compare(s0, s1)).isNegative();
    assertThat(headerComparator.compare(s1, s0)).isPositive();
  }

  @Test
  public void testComparsionEqual() {
    LedgerHeader l0 = mock(LedgerHeader.class);
    when(l0.getEpoch()).thenReturn(2L);
    AccumulatorState accumulatorState = mock(AccumulatorState.class);
    when(accumulatorState.getStateVersion()).thenReturn(3L);
    when(l0.getAccumulatorState()).thenReturn(accumulatorState);
    when(l0.isEndOfEpoch()).thenReturn(true);
    LedgerProof s0 =
        new LedgerProof(HashUtils.random256(), l0, mock(TimestampedECDSASignatures.class));
    LedgerHeader l1 = mock(LedgerHeader.class);
    when(l1.getEpoch()).thenReturn(2L);
    AccumulatorState accumulatorState1 = mock(AccumulatorState.class);
    when(accumulatorState1.getStateVersion()).thenReturn(3L);
    when(l1.getAccumulatorState()).thenReturn(accumulatorState1);
    when(l1.isEndOfEpoch()).thenReturn(true);
    LedgerProof s1 =
        new LedgerProof(HashUtils.random256(), l1, mock(TimestampedECDSASignatures.class));
    assertThat(headerComparator.compare(s0, s1)).isZero();
    assertThat(headerComparator.compare(s1, s0)).isZero();
  }

  @Test(expected = NullPointerException.class)
  public void deserializationWithNullThrowsException1() {
    new LedgerProof(null, mock(LedgerHeader.class), mock(TimestampedECDSASignatures.class));
  }

  @Test(expected = NullPointerException.class)
  public void deserializationWithNullThrowsException2() {
    new LedgerProof(mock(HashCode.class), null, mock(TimestampedECDSASignatures.class));
  }

  @Test(expected = NullPointerException.class)
  public void deserializationWithNullThrowsException3() {
    new LedgerProof(mock(HashCode.class), mock(LedgerHeader.class), null);
  }
}
