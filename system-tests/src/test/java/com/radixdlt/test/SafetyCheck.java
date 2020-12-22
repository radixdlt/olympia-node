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
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * A safety check scanning the nodes for any safety violation. This check uses the "api/vertices/committed" endpoint
 * to assert that nodes are committed to the same vertex given selected samples provided by the nodes. Specifically,
 * this check fails if there is any pair of [{"view": 1, "hash": "abc"}] and [{"view": 1, "hash": "xyz"}] with
 * the same "view" but a different "hash" returned by the network (could even be a single node if malfunctioning).
 *
 * Note that the "api/vertices/committed" endpoint returns only a (deterministic) subset of committed vertices as
 * configured in RadixCore and this test therefore relies on the implicit hash chain formed by vertices to
 * assert safety (where every vertex includes a reference to the last).
 */
public class SafetyCheck implements RemoteBFTCheck {
	private static final Logger logger = LogManager.getLogger();

	private final long timeout;
	private final TimeUnit timeoutUnit;
	private List<String> nodesToIgnore;

	private SafetyCheck(long timeout, TimeUnit timeoutUnit , List<String> nodesToIgnore) {
		if (timeout < 1) {
			throw new IllegalArgumentException("timeout must be >= 1 but was " + timeout);
		}
		this.timeout = timeout;
		this.timeoutUnit = Objects.requireNonNull(timeoutUnit);
		this.nodesToIgnore = nodesToIgnore;
	}

	public static SafetyCheck with(long timeout, TimeUnit timeoutUnit) {

		return new SafetyCheck(timeout, timeoutUnit,new ArrayList<String>());
	}

	public SafetyCheck withNodesToIgnore(List<String> nodesToIgnore) {
		this.nodesToIgnore = nodesToIgnore;
		return this;
	}


	@Override
	public Single<RemoteBFTCheckResult> check(RemoteBFTNetworkBridge network) {
		return Single.zip(
			network.getNodeIds().stream()
				.filter(nodename -> !nodesToIgnore.contains(nodename))
				.map(node -> network.queryEndpoint(node, "api/vertices/committed")
					.timeout(timeout, timeoutUnit)
					.map(verticesString -> {
						logger.debug("Api/Vertices/commited endpoint response {} ",verticesString);
						return extractVertices(verticesString, node);
					})
					.doOnError(err -> logger.warn(
						"error while querying {} for committed vertices, excluding from evaluation due to: {}",
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

	/**
	 * Extracts vertices from a given string and node
	 * @param verticesString The string representing the vertices
	 * @param node The node the string originates from
	 * @return An extracted set of {@link Vertex}
	 */
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

	/**
	 * Gets the dissenting vertices given a collection of vertices, where a {@link Vertex} is considered to dissent
	 * from another {@link Vertex} if its view is the same but hash is different.
	 * @param vertices The vertices
	 * @return The dissenting vertices, grouped by hash, grouped by view
	 */
	private static Map<EpochView, Map<String, List<Vertex>>> getDissentingVertices(Collection<Vertex> vertices) {
		ImmutableMap.Builder<EpochView, Map<String, List<Vertex>>> dissentingVertices = ImmutableMap.builder();
		Map<EpochView, List<Vertex>> verticesByView = vertices.stream()
			.collect(Collectors.groupingBy(Vertex::getEpochView));
		for (List<Vertex> verticesAtView : verticesByView.values()) {
			Map<String, List<Vertex>> verticesByHash = verticesAtView.stream()
				.collect(Collectors.groupingBy(Vertex::getHash));
			if (verticesByHash.size() > 1) {
				dissentingVertices.put(verticesAtView.get(0).getEpochView(), verticesByHash);
			}
		}
		return dissentingVertices.build();
	}

	private static class EpochView {
		private final long epoch;
		private final long view;

		private EpochView(long epoch, long view) {
			this.epoch= epoch;
			this.view = view;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof EpochView)) {
				return false;
			}
			EpochView epochView = (EpochView) o;
			return epochView.epoch == this.epoch
				&& epochView.view == this.view;
		}

		@Override
		public int hashCode() {
			return Objects.hash(epoch, view);
		}

		@Override
		public String toString() {
			return String.format("{epoch=%s view=%s}", epoch, view);
		}
	}

	/**
	 * A simplified representation of a Cerberus vertex.
	 */
	private static final class Vertex {
		private final EpochView epochView;
		private final String hash; // TODO reconsider String for "hash"
		private final String node;

		private Vertex(long epoch, long view, String hash, String node) {
			if (view < 0) {
				throw new IllegalArgumentException(String.format("view must be > 0, got %d", view));
			}
			this.epochView = new EpochView(epoch, view);
			this.hash = Objects.requireNonNull(hash);
			this.node = Objects.requireNonNull(node);
		}

		private static Vertex from(JSONObject vertexJson, String node) {
			return new Vertex(
				vertexJson.getLong("epoch"),
				vertexJson.getLong("view"),
				vertexJson.getString("hash"),
				node
			);
		}

		private EpochView getEpochView() {
			return epochView;
		}

		private String getHash() {
			return hash;
		}

		private String getNode() {
			return node;
		}
	}

	/**
	 * An error that is thrown if a safety violation occurs
	 */
	public static class SafetyViolationError extends AssertionError {
		private final Map<EpochView, Map<String, List<Vertex>>> dissentingVertices;

		private SafetyViolationError(Map<EpochView, Map<String, List<Vertex>>> dissentingVertices) {
			super(dissentingVertices.entrySet().stream()
					.map(verticesAtView -> String.format("%s={%s}",
						verticesAtView.getKey(),
						String.join(", ", verticesAtView.getValue().keySet())))
					.collect(Collectors.joining("; ")));
			this.dissentingVertices = dissentingVertices;
		}

		public Map<EpochView, Map<String, List<Vertex>>> getDissentingVertices() {
			return dissentingVertices;
		}
	}
}
