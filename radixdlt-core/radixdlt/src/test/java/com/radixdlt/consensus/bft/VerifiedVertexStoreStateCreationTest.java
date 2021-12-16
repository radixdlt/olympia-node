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

package com.radixdlt.consensus.bft;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.AccumulatorState;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class VerifiedVertexStoreStateCreationTest {
  private VerifiedVertex genesisVertex;
  private HashCode genesisHash;
  private Hasher hasher;
  private static final LedgerHeader MOCKED_HEADER =
      LedgerHeader.create(0, View.genesis(), new AccumulatorState(0, HashUtils.zero256()), 0);

  @Before
  public void setup() {
    this.genesisHash = HashUtils.zero256();
    this.genesisVertex =
        new VerifiedVertex(UnverifiedVertex.createGenesis(MOCKED_HEADER), genesisHash);
    this.hasher = new Sha256Hasher(DefaultSerialization.getInstance());
  }

  @Test
  public void creating_vertex_store_with_root_not_committed_should_fail() {
    BFTHeader genesisHeader = new BFTHeader(View.of(0), genesisHash, mock(LedgerHeader.class));
    VoteData voteData = new VoteData(genesisHeader, genesisHeader, null);
    QuorumCertificate badRootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
    assertThatThrownBy(
            () ->
                VerifiedVertexStoreState.create(
                    HighQC.from(badRootQC), genesisVertex, Optional.empty(), hasher))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void creating_vertex_store_with_committed_qc_not_matching_vertex_should_fail() {
    BFTHeader genesisHeader = new BFTHeader(View.of(0), genesisHash, mock(LedgerHeader.class));
    BFTHeader otherHeader =
        new BFTHeader(View.of(0), HashUtils.random256(), mock(LedgerHeader.class));
    VoteData voteData = new VoteData(genesisHeader, genesisHeader, otherHeader);
    QuorumCertificate badRootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
    assertThatThrownBy(
            () ->
                VerifiedVertexStoreState.create(
                    HighQC.from(badRootQC), genesisVertex, Optional.empty(), hasher))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void creating_vertex_store_with_qc_not_matching_vertex_should_fail() {
    BFTHeader genesisHeader =
        new BFTHeader(View.of(0), HashUtils.random256(), mock(LedgerHeader.class));
    VoteData voteData = new VoteData(genesisHeader, genesisHeader, genesisHeader);
    QuorumCertificate badRootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
    assertThatThrownBy(
            () ->
                VerifiedVertexStoreState.create(
                    HighQC.from(badRootQC), genesisVertex, Optional.empty(), hasher))
        .isInstanceOf(IllegalStateException.class);
  }
}
