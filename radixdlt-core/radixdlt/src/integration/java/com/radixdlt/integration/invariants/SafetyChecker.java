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

package com.radixdlt.integration.invariants;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.integration.distributed.simulation.TestInvariant.TestInvariantError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Processes committed vertices and verifies that it forms a single
 * chain without any forks.
 */
@NotThreadSafe
public final class SafetyChecker {
	private final TreeMap<EpochView, VerifiedVertex> committedVertices = new TreeMap<>();
	private final Map<BFTNode, EpochView> lastCommittedByNode = new HashMap<>();
	private final List<BFTNode> nodes;

	@Inject
	public SafetyChecker(List<BFTNode> nodes) {
		this.nodes = Objects.requireNonNull(nodes);
	}

	private static Optional<TestInvariantError> conflictingVerticesError(VerifiedVertex vertex, VerifiedVertex currentVertex) {
		return Optional.of(
			new TestInvariantError(
				String.format("Conflicting vertices [%s, %s] committed at same view: %s",
					vertex,
					currentVertex,
					vertex.getView()
				)
			)
		);
	}

	private static Optional<TestInvariantError> brokenChainError(VerifiedVertex vertex, VerifiedVertex closeVertex) {
		return Optional.of(
			new TestInvariantError(
				String.format("Broken Chain [%s, %s]",
					vertex,
					closeVertex
				)
			)
		);
	}

	private Optional<TestInvariantError> process(BFTNode node, VerifiedVertex vertex) {
		final EpochView epochView = EpochView.of(
			vertex.getParentHeader().getLedgerHeader().getEpoch(),
			vertex.getView()
		);

		final VerifiedVertex currentVertexAtView = committedVertices.get(epochView);
		if (currentVertexAtView != null) {
			if (!currentVertexAtView.getId().equals(vertex.getId())) {
				return conflictingVerticesError(vertex, currentVertexAtView);
			}
		} else {
			EpochView parentEpochView = EpochView.of(
				vertex.getParentHeader().getLedgerHeader().getEpoch(),
				vertex.getParentHeader().getView()
			);
			VerifiedVertex parent = committedVertices.get(parentEpochView);
			if (parent == null) {
				Entry<EpochView, VerifiedVertex> higherCommitted = committedVertices.higherEntry(parentEpochView);
				if (higherCommitted != null) {
					BFTHeader higherParentHeader = higherCommitted.getValue().getParentHeader();
					EpochView higherCommittedParentEpochView = EpochView.of(
						higherParentHeader.getLedgerHeader().getEpoch(),
						higherParentHeader.getView()
					);
					if (epochView.compareTo(higherCommittedParentEpochView) > 0) {
						return brokenChainError(vertex, higherCommitted.getValue());
					}
				}
			}

			committedVertices.put(epochView, vertex);
		}

		// Clean up old vertices so that we avoid consuming too much memory
		lastCommittedByNode.put(node, epochView);
		final EpochView lowest = nodes.stream()
			.map(n -> lastCommittedByNode.getOrDefault(n, EpochView.of(0, View.genesis())))
			.reduce((v0, v1) -> v0.compareTo(v1) < 0 ? v0 : v1)
			.orElse(EpochView.of(0, View.genesis()));
		committedVertices.headMap(lowest).clear();

		return Optional.empty();
	}

	public Optional<TestInvariantError> process(BFTNode node, BFTCommittedUpdate committedUpdate) {
		ImmutableList<PreparedVertex> vertices = committedUpdate.getCommitted();
		for (PreparedVertex vertex : vertices) {
			Optional<TestInvariantError> maybeError = process(node, vertex.getVertex());
			if (maybeError.isPresent()) {
				return maybeError;
			}
		}

		return Optional.empty();
	}
}
