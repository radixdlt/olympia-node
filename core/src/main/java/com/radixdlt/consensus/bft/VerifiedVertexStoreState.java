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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.store.berkeley.SerializedVertexStoreState;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/** State of the vertex store which can be serialized. */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Immutable
public final class VerifiedVertexStoreState {
  private final VerifiedVertex root;
  private final LedgerProof rootHeader;
  private final HighQC highQC;
  // TODO: collapse the following two
  private final ImmutableList<VerifiedVertex> vertices;
  private final ImmutableMap<HashCode, VerifiedVertex> idToVertex;
  private Optional<TimeoutCertificate> highestTC;

  private VerifiedVertexStoreState(
      HighQC highQC,
      LedgerProof rootHeader,
      VerifiedVertex root,
      ImmutableMap<HashCode, VerifiedVertex> idToVertex,
      ImmutableList<VerifiedVertex> vertices,
      Optional<TimeoutCertificate> highestTC) {
    this.highQC = highQC;
    this.rootHeader = rootHeader;
    this.root = root;
    this.idToVertex = idToVertex;
    this.vertices = vertices;
    this.highestTC = highestTC;
  }

  public static VerifiedVertexStoreState create(
      HighQC highQC, VerifiedVertex root, Optional<TimeoutCertificate> highestTC, Hasher hasher) {
    return create(highQC, root, ImmutableList.of(), highestTC, hasher);
  }

  public static VerifiedVertexStoreState create(
      HighQC highQC,
      VerifiedVertex root,
      ImmutableList<VerifiedVertex> vertices,
      Optional<TimeoutCertificate> highestTC,
      Hasher hasher) {
    final var headers =
        highQC
            .highestCommittedQC()
            .getCommittedAndLedgerStateProof(hasher)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format("highQC=%s does not have commit", highQC)));
    var bftHeader = headers.getFirst();

    if (!bftHeader.getVertexId().equals(root.getId())) {
      throw new IllegalStateException(
          String.format("committedHeader=%s does not match rootVertex=%s", bftHeader, root));
    }

    var seen = new HashMap<HashCode, VerifiedVertex>();
    seen.put(root.getId(), root);

    for (var vertex : vertices) {
      if (!seen.containsKey(vertex.getParentId())) {
        throw new IllegalStateException(
            String.format("Missing qc=%s {root=%s vertices=%s}", vertex.getQC(), root, vertices));
      }
      seen.put(vertex.getId(), vertex);
    }

    if (seen.keySet().stream()
        .noneMatch(highQC.highestCommittedQC().getProposed().getVertexId()::equals)) {
      throw new IllegalStateException(
          String.format(
              "highQC=%s highCommitted proposed missing {root=%s vertices=%s}",
              highQC, root, vertices));
    }

    if (seen.keySet().stream()
        .noneMatch(highQC.highestCommittedQC().getParent().getVertexId()::equals)) {
      throw new IllegalStateException(
          String.format(
              "highQC=%s highCommitted parent does not have a corresponding vertex", highQC));
    }

    if (seen.keySet().stream().noneMatch(highQC.highestQC().getParent().getVertexId()::equals)) {
      throw new IllegalStateException(
          String.format("highQC=%s highQC parent does not have a corresponding vertex", highQC));
    }

    if (seen.keySet().stream().noneMatch(highQC.highestQC().getProposed().getVertexId()::equals)) {
      throw new IllegalStateException(
          String.format("highQC=%s highQC proposed does not have a corresponding vertex", highQC));
    }

    return new VerifiedVertexStoreState(
        highQC, headers.getSecond(), root, ImmutableMap.copyOf(seen), vertices, highestTC);
  }

  public VerifiedVertexStoreState prune(Hasher hasher) {
    var stateProof = highQC.highestQC().getCommittedAndLedgerStateProof(hasher);
    if (stateProof.isPresent()) {
      var newHeaders = stateProof.get();
      var header = newHeaders.getFirst();

      if (header.getView().gt(root.getView())) {
        var newRoot = idToVertex.get(header.getVertexId());
        var newVertices =
            ImmutableList.of(
                idToVertex.get(highQC.highestQC().getParent().getVertexId()),
                idToVertex.get(highQC.highestQC().getProposed().getVertexId()));
        var idToVertexMap =
            ImmutableMap.of(
                highQC.highestQC().getParent().getVertexId(), newVertices.get(0),
                highQC.highestQC().getProposed().getVertexId(), newVertices.get(1));
        var newHighQC = HighQC.from(highQC.highestQC());
        var proof = newHeaders.getSecond();
        return new VerifiedVertexStoreState(
            newHighQC, proof, newRoot, idToVertexMap, newVertices, highestTC);
      }
    }

    return this;
  }

  public SerializedVertexStoreState toSerialized() {
    return new SerializedVertexStoreState(
        this.highQC,
        this.root.toSerializable(),
        this.vertices.stream()
            .map(VerifiedVertex::toSerializable)
            .collect(ImmutableList.toImmutableList()),
        this.highestTC.orElse(null));
  }

  public HighQC getHighQC() {
    return highQC;
  }

  public VerifiedVertex getRoot() {
    return root;
  }

  public ImmutableList<VerifiedVertex> getVertices() {
    return vertices;
  }

  public LedgerProof getRootHeader() {
    return rootHeader;
  }

  @Override
  public int hashCode() {
    return Objects.hash(root, rootHeader, highQC, idToVertex, vertices, highestTC);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    return o instanceof VerifiedVertexStoreState other
        && Objects.equals(this.root, other.root)
        && Objects.equals(this.rootHeader, other.rootHeader)
        && Objects.equals(this.highQC, other.highQC)
        && Objects.equals(this.vertices, other.vertices)
        && Objects.equals(this.idToVertex, other.idToVertex)
        && Objects.equals(this.highestTC, other.highestTC);
  }
}
