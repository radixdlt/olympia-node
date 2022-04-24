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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class VertexStoreTest {
  private VerifiedVertex genesisVertex;
  private Supplier<VerifiedVertex> nextVertex;
  private Function<Boolean, VerifiedVertex> nextSkippableVertex;
  private HashCode genesisHash;
  private QuorumCertificate rootQC;
  private VertexStore sut;
  private Ledger ledger;
  private EventDispatcher<BFTInsertUpdate> bftUpdateSender;
  private EventDispatcher<BFTRebuildUpdate> rebuildUpdateEventDispatcher;
  private EventDispatcher<BFTHighQCUpdate> bftHighQCUpdateEventDispatcher;
  private EventDispatcher<BFTCommittedUpdate> committedSender;
  private Hasher hasher = Sha256Hasher.withDefaultSerialization();

  private static final LedgerHeader MOCKED_HEADER =
      LedgerHeader.create(0, View.genesis(), new AccumulatorState(0, HashUtils.zero256()), 0);

  @Before
  public void setUp() {
    // No type check issues with mocking generic here
    Ledger ssc = mock(Ledger.class);
    this.ledger = ssc;
    // TODO: replace mock with the real thing
    doAnswer(
            invocation -> {
              VerifiedVertex verifiedVertex = invocation.getArgument(1);
              return Optional.of(
                  new PreparedVertex(
                      verifiedVertex, MOCKED_HEADER, ImmutableList.of(), ImmutableMap.of(), 1L));
            })
        .when(ledger)
        .prepare(any(), any());

    this.bftUpdateSender = rmock(EventDispatcher.class);
    this.rebuildUpdateEventDispatcher = rmock(EventDispatcher.class);
    this.bftHighQCUpdateEventDispatcher = rmock(EventDispatcher.class);
    this.committedSender = rmock(EventDispatcher.class);

    this.genesisHash = HashUtils.zero256();
    this.genesisVertex =
        new VerifiedVertex(UnverifiedVertex.createGenesis(MOCKED_HEADER), genesisHash);
    this.rootQC = QuorumCertificate.ofGenesis(genesisVertex, MOCKED_HEADER);
    this.sut =
        VertexStore.create(
            VerifiedVertexStoreState.create(
                HighQC.from(rootQC), genesisVertex, Optional.empty(), hasher),
            ledger,
            hasher,
            bftUpdateSender,
            rebuildUpdateEventDispatcher,
            bftHighQCUpdateEventDispatcher,
            committedSender);

    AtomicReference<BFTHeader> lastParentHeader =
        new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, MOCKED_HEADER));
    AtomicReference<BFTHeader> lastGrandParentHeader =
        new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, MOCKED_HEADER));
    AtomicReference<BFTHeader> lastGreatGrandParentHeader =
        new AtomicReference<>(new BFTHeader(View.genesis(), genesisHash, MOCKED_HEADER));

    this.nextSkippableVertex =
        (skipOne) -> {
          BFTHeader parentHeader = lastParentHeader.get();
          BFTHeader grandParentHeader = lastGrandParentHeader.get();
          BFTHeader greatGrandParentHeader = lastGreatGrandParentHeader.get();
          final QuorumCertificate qc;
          if (!parentHeader.getView().equals(View.genesis())) {
            VoteData data =
                new VoteData(
                    parentHeader, grandParentHeader, skipOne ? null : greatGrandParentHeader);
            qc = new QuorumCertificate(data, new TimestampedECDSASignatures());
          } else {
            qc = rootQC;
          }
          View view = parentHeader.getView().next();
          if (skipOne) {
            view = view.next();
          }

          var rawVertex =
              UnverifiedVertex.create(qc, view, List.of(Txn.create(new byte[0])), BFTNode.random());
          HashCode hash = hasher.hash(rawVertex);
          VerifiedVertex vertex = new VerifiedVertex(rawVertex, hash);
          lastParentHeader.set(new BFTHeader(view, hash, MOCKED_HEADER));
          lastGrandParentHeader.set(parentHeader);
          lastGreatGrandParentHeader.set(grandParentHeader);

          return vertex;
        };

    this.nextVertex = () -> nextSkippableVertex.apply(false);
  }

  @Test
  public void adding_a_qc_should_update_highest_qc() {
    // Arrange
    final var vertices = Stream.generate(this.nextVertex).limit(4).toList();
    sut.insertVertex(vertices.get(0));

    // Act
    QuorumCertificate qc = vertices.get(1).getQC();
    sut.addQC(qc);

    // Assert
    assertThat(sut.highQC().highestQC()).isEqualTo(qc);
    assertThat(sut.highQC().highestCommittedQC()).isEqualTo(rootQC);
  }

  @Test
  public void adding_a_qc_with_commit_should_commit_vertices_to_ledger() {
    // Arrange
    final var vertices = Stream.generate(this.nextVertex).limit(4).toList();
    sut.insertVertex(vertices.get(0));
    sut.insertVertex(vertices.get(1));
    sut.insertVertex(vertices.get(2));

    // Act
    QuorumCertificate qc = vertices.get(3).getQC();
    boolean success = sut.addQC(qc);

    // Assert
    assertThat(success).isTrue();
    assertThat(sut.highQC().highestQC()).isEqualTo(qc);
    assertThat(sut.highQC().highestCommittedQC()).isEqualTo(qc);
    assertThat(sut.getVertices(vertices.get(2).getId(), 3))
        .hasValue(ImmutableList.of(vertices.get(2), vertices.get(1), vertices.get(0)));
    verify(committedSender, times(1))
        .dispatch(
            argThat(
                u ->
                    u.committed().size() == 1
                        && u.committed().get(0).getVertex().equals(vertices.get(0))));
  }

  @Test
  public void adding_a_qc_which_has_not_been_inserted_should_return_false() {
    // Arrange
    this.nextVertex.get();

    // Act
    QuorumCertificate qc = this.nextVertex.get().getQC();
    boolean success = sut.addQC(qc);

    // Assert
    assertThat(success).isFalse();
  }

  @Test
  public void rebuilding_should_emit_updates() {
    // Arrange
    final var vertices = Stream.generate(this.nextVertex).limit(4).toList();
    VerifiedVertexStoreState vertexStoreState =
        VerifiedVertexStoreState.create(
            HighQC.from(vertices.get(3).getQC()),
            vertices.get(0),
            vertices.stream().skip(1).collect(ImmutableList.toImmutableList()),
            sut.getHighestTimeoutCertificate(),
            hasher);

    // Act
    sut.tryRebuild(vertexStoreState);

    // Assert
    verify(rebuildUpdateEventDispatcher, times(1))
        .dispatch(
            argThat(
                u -> {
                  List<VerifiedVertex> sentVertices = u.getVertexStoreState().getVertices();
                  return sentVertices.equals(vertices.subList(1, vertices.size()));
                }));
  }

  @Test
  public void inserting_a_tc_should_only_replace_tcs_for_lower_views() {
    TimeoutCertificate initialTC =
        new TimeoutCertificate(1, View.of(100), mock(TimestampedECDSASignatures.class));
    TimeoutCertificate higherTC =
        new TimeoutCertificate(1, View.of(101), mock(TimestampedECDSASignatures.class));

    sut.insertTimeoutCertificate(initialTC);
    assertEquals(initialTC, sut.getHighestTimeoutCertificate().orElse(null));

    sut.insertTimeoutCertificate(higherTC);
    assertEquals(higherTC, sut.getHighestTimeoutCertificate().orElse(null));

    sut.insertTimeoutCertificate(initialTC);
    assertEquals(higherTC, sut.getHighestTimeoutCertificate().orElse(null));
  }
}
