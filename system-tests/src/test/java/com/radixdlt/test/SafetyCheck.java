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
 *
 */

package com.radixdlt.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.reactivex.Single;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Checks that nodes are committed to the same vertex given selected samples provided by the nodes.
 */
public class SafetyCheck implements RemoteBFTCheck {
	private final int timeout;
	private final TimeUnit timeoutUnit;

	public SafetyCheck(int timeout, TimeUnit timeoutUnit) {
		this.timeout = timeout;
		this.timeoutUnit = Objects.requireNonNull(timeoutUnit);
	}

	@Override
	public Single<RemoteBFTCheckResult> check(RemoteBFTNetworkBridge network) {
		return Single.zip(
			network.getNodeIds().stream()
				.map(node -> network.queryEndpoint(node, "api/vertices/committed")
					.timeout(timeout, timeoutUnit)
					.map(verticesString -> extractVertices(verticesString, node))
					.doOnError(err -> System.err.printf(
						"error while querying %s for committed vertices, excluding from evaluation due to: %s%n",
						node, err))
					.onErrorReturnItem(ImmutableSet.of())) // unresponsive nodes are not our concern here
				.collect(Collectors.toList()),
			verticesAtNodes -> Arrays.stream(verticesAtNodes)
				.flatMap(vertices -> ((Set<Vertex>) vertices).stream())
				.collect(Collectors.toList()))
			.map(SafetyCheck::getDissentingVertices)
			.map(dissentingVertices -> {
				if (dissentingVertices.isEmpty()) {
					return RemoteBFTCheckResult.success();
				} else {
					return RemoteBFTCheckResult.error(new SafetyViolationError(dissentingVertices));
				}
			});
	}

	@Override
	public String toString() {
		return String.format("SafetyCheck{timeout=%d %s}", timeout, timeoutUnit);
	}

	private static Set<Vertex> extractVertices(String verticesString, String node) {
		JSONArray verticesJson = new JSONArray(verticesString);
		ImmutableSet.Builder<Vertex> vertices = ImmutableSet.builder();
		for (int j = 0; j < verticesJson.length(); j++) {
			JSONObject vertexJson = verticesJson.getJSONObject(j);
			Vertex vertex = Vertex.from(vertexJson, node);
			vertices.add(vertex);
		}
		return vertices.build();
	}

	private static Map<Long, Map<String, List<Vertex>>> getDissentingVertices(Collection<Vertex> vertices) {
		ImmutableMap.Builder<Long, Map<String, List<Vertex>>> dissentingVertices = ImmutableMap.builder();
		Map<Long, List<Vertex>> verticesByView = vertices.stream()
			.collect(Collectors.groupingBy(Vertex::getView));
		for (List<Vertex> verticesAtView : verticesByView.values()) {
			Map<String, List<Vertex>> verticesByHash = verticesAtView.stream()
				.collect(Collectors.groupingBy(Vertex::getHash));
			if (verticesByHash.size() > 1) {
				dissentingVertices.put(verticesAtView.get(0).getView(), verticesByHash);
			}
		}
		return dissentingVertices.build();
	}

	private static final class Vertex {
		private final long view;
		private final String hash;
		private final String node;

		private Vertex(long view, String hash, String node) {
			if (view < 0) {
				throw new IllegalArgumentException(String.format("view must be > 0, got %d", view));
			}
			this.view = view;
			this.hash = Objects.requireNonNull(hash);
			this.node = Objects.requireNonNull(node);
		}

		private static Vertex from(JSONObject vertexJson, String node) {
			return new Vertex(
				vertexJson.getLong("view"),
				vertexJson.getString("hash"),
				node
			);
		}

		private long getView() {
			return view;
		}

		private String getHash() {
			return hash;
		}

		private String getNode() {
			return node;
		}
	}

	public static class SafetyViolationError extends AssertionError {
		private final Map<Long, Map<String, List<Vertex>>> dissentingVertices;

		public SafetyViolationError(Map<Long, Map<String, List<Vertex>>> dissentingVertices) {
			super(dissentingVertices.entrySet().stream()
					.map(verticesAtView -> String.format("%d={%s}",
						verticesAtView.getKey(),
						String.join(", ", verticesAtView.getValue().keySet())))
					.collect(Collectors.joining("; ")));
			this.dissentingVertices = dissentingVertices;
		}

		public Map<Long, Map<String, List<Vertex>>> getDissentingVertices() {
			return dissentingVertices;
		}
	}
}
