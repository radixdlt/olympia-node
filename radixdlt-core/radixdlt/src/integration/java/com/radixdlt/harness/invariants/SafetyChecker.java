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

package com.radixdlt.harness.invariants;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.harness.simulation.TestInvariant.TestInvariantError;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.concurrent.NotThreadSafe;

/** Processes committed vertices and verifies that it forms a single chain without any forks. */
@NotThreadSafe
public final class SafetyChecker {
  private final TreeMap<EpochView, VerifiedVertex> committedVertices = new TreeMap<>();
  private final Map<BFTNode, EpochView> lastCommittedByNode = new HashMap<>();
  private final ImmutableSet<BFTNode> nodes;

  @Inject
  public SafetyChecker(ImmutableSet<BFTNode> nodes) {
    this.nodes = Objects.requireNonNull(nodes);
  }

  private static Optional<TestInvariantError> conflictingVerticesError(
      VerifiedVertex vertex, VerifiedVertex currentVertex) {
    return Optional.of(
        new TestInvariantError(
            String.format(
                "Conflicting vertices [%s, %s] committed at same view: %s",
                vertex, currentVertex, vertex.getView())));
  }

  private static Optional<TestInvariantError> brokenChainError(
      VerifiedVertex vertex, VerifiedVertex closeVertex) {
    return Optional.of(
        new TestInvariantError(String.format("Broken Chain [%s, %s]", vertex, closeVertex)));
  }

  private Optional<TestInvariantError> process(BFTNode node, VerifiedVertex vertex) {
    final EpochView epochView =
        EpochView.of(vertex.getParentHeader().getLedgerHeader().getEpoch(), vertex.getView());

    final VerifiedVertex currentVertexAtView = committedVertices.get(epochView);
    if (currentVertexAtView != null) {
      if (!currentVertexAtView.getId().equals(vertex.getId())) {
        return conflictingVerticesError(vertex, currentVertexAtView);
      }
    } else {
      EpochView parentEpochView =
          EpochView.of(
              vertex.getParentHeader().getLedgerHeader().getEpoch(),
              vertex.getParentHeader().getView());
      VerifiedVertex parent = committedVertices.get(parentEpochView);
      if (parent == null) {
        Entry<EpochView, VerifiedVertex> higherCommitted =
            committedVertices.higherEntry(parentEpochView);
        if (higherCommitted != null) {
          BFTHeader higherParentHeader = higherCommitted.getValue().getParentHeader();
          EpochView higherCommittedParentEpochView =
              EpochView.of(
                  higherParentHeader.getLedgerHeader().getEpoch(), higherParentHeader.getView());
          if (epochView.compareTo(higherCommittedParentEpochView) > 0) {
            return brokenChainError(vertex, higherCommitted.getValue());
          }
        }
      }

      committedVertices.put(epochView, vertex);
    }

    // Clean up old vertices so that we avoid consuming too much memory
    lastCommittedByNode.put(node, epochView);
    final EpochView lowest =
        nodes.stream()
            .map(n -> lastCommittedByNode.getOrDefault(n, EpochView.of(0, View.genesis())))
            .reduce((v0, v1) -> v0.compareTo(v1) < 0 ? v0 : v1)
            .orElse(EpochView.of(0, View.genesis()));
    committedVertices.headMap(lowest).clear();

    return Optional.empty();
  }

  public Optional<TestInvariantError> process(BFTNode node, BFTCommittedUpdate committedUpdate) {
    ImmutableList<PreparedVertex> vertices = committedUpdate.committed();
    for (PreparedVertex vertex : vertices) {
      Optional<TestInvariantError> maybeError = process(node, vertex.getVertex());
      if (maybeError.isPresent()) {
        return maybeError;
      }
    }

    return Optional.empty();
  }
}
