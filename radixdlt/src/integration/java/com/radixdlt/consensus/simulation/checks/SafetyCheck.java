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

package com.radixdlt.consensus.simulation.checks;

import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.BFTCheck;
import com.radixdlt.consensus.simulation.SimulatedBFTNetwork;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Checks that nodes do not commit on conflicting vertices
 */
public class SafetyCheck implements BFTCheck {

	private static Observable<BFTCheckError> differentVerticesError(Vertex vertex, Vertex currentVertex) {
		return Observable.just(
			new BFTCheckError(
				String.format("Different vertices [%s, %s] committed at same view: %s",
					vertex,
					currentVertex,
					vertex.getView()
				)
			)
		);
	}

	private static Observable<BFTCheckError> noParentError(Vertex vertex) {
		return Observable.just(
			new BFTCheckError(
				String.format("Committed vertex %s has no parent", vertex)
			)
		);
	}

	private static Observable<BFTCheckError> badParentError(Vertex vertex, Vertex lastCommitted) {
		return Observable.just(
			new BFTCheckError(
				String.format("Parent of vertex %s doesn't match last committed %s",
					vertex,
					lastCommitted
				)
			)
		);
	}

	@Override
	public Observable<BFTCheckError> check(SimulatedBFTNetwork network) {
		final Map<View, Vertex> committedVertices = new ConcurrentHashMap<>();
		committedVertices.put(View.genesis(), network.getGenesisVertex());
		final AtomicReference<View> highest = new AtomicReference<>(View.genesis());
		final Map<ECKeyPair, Vertex> lastCommittedByNode = new ConcurrentHashMap<>();

		return Observable.merge(
			network.getNodes().stream().map(
				node -> network.getVertexStore(node)
					.lastCommittedVertex().map(v -> Pair.of(node, v)))
				.collect(Collectors.toList())
		)
			.flatMap(nodeAndVertex -> {
				final ECKeyPair node = nodeAndVertex.getFirst();
				final Vertex vertex = nodeAndVertex.getSecond();
				final Vertex currentVertexAtView = committedVertices.get(vertex.getView());
				if (currentVertexAtView != null) {
					if (!currentVertexAtView.getId().equals(vertex.getId())) {
						return differentVerticesError(vertex, currentVertexAtView);
					}
				} else {
					final Vertex lastCommitted = committedVertices.get(highest.get());
					if (vertex.getParentId() == null) {
						return noParentError(vertex);
					}

					if (!vertex.getParentId().equals(lastCommitted.getId())
						|| !vertex.getParentView().equals(lastCommitted.getView())) {
						return badParentError(vertex, lastCommitted);
					}

					committedVertices.put(vertex.getView(), vertex);
					highest.set(vertex.getView());
				}

				// Clean up old vertices so that we avoid consuming too much memory
				lastCommittedByNode.put(node, vertex);
				final View lowest = lastCommittedByNode.values().stream()
					.map(Vertex::getView)
					.reduce((v0, v1) -> v0.compareTo(v1) < 0 ? v0 : v1)
					.orElse(View.genesis());
				final Set<View> viewsToRemove = committedVertices.keySet().stream()
					.filter(v -> v.compareTo(lowest) < 0)
					.collect(Collectors.toSet());
				for (View viewToRemove : viewsToRemove) {
					committedVertices.remove(viewToRemove);
				}

				return Observable.empty();
			});
	}
}
