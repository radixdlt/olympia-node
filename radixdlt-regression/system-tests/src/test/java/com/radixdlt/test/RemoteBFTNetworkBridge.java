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

import com.radixdlt.client.core.network.HttpClients;
import io.reactivex.Completable;
import io.reactivex.Single;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A bridge between a test and a certain {@link RemoteBFTNetwork} implementation,
 * providing utility methods to operate on remote networks while abstracting away the details of the network.
 */
public final class RemoteBFTNetworkBridge {
	private static final Logger log = LogManager.getLogger();
	private static final AtomicInteger requestId = new AtomicInteger();

	private final RemoteBFTNetwork network;
	private final OkHttpClient client;

	private RemoteBFTNetworkBridge(RemoteBFTNetwork network) {
		this.network = network;
		this.client = HttpClients.getSslAllTrustingClient();
	}

	/**
	 * Creates a cold {@link Single} which makes the specified request and returns its response when subscribed to.
	 *
	 * @param request The request to make upon subscription
	 * @return The cold {@link Single} that will contain the response body if successful or error if failure
	 */
	private Single<String> makeRequest(Request request) {
		final var newRequestId = requestId.incrementAndGet();
		final var newRequestTime = System.currentTimeMillis();
		log.debug("Request {}: {}", newRequestId, request);
		return Single.create(emitter -> {
			Call call = this.client.newCall(request);
			call.enqueue(new Callback() {
				@Override
				public void onFailure(Call call, IOException e) {
					log.info("=".repeat(50) + "\n" + call.request().url() + "\n" + call.request().body() + "\n" +  "=".repeat(50));
					logRequestFailed(newRequestId, newRequestTime, e);
					emitter.onError(e);
				}

				@Override
				public void onResponse(Call call, Response response) throws IOException {
					if (response.isSuccessful()) {
						try {
							String responseString = response.body().string();
							logRequestSuccessful(newRequestId, newRequestTime, responseString);
							emitter.onSuccess(responseString);
						} catch (IOException e) {
							log.info("=".repeat(50) + "\n" + call.request().url() + "\n" + call.request().body() + "\n" +  "=".repeat(50));
							log.info("=".repeat(50) + "\n" + response.body() + "\n" + "=".repeat(50));

							logRequestFailed(newRequestId, newRequestTime, e);
							emitter.onError(new IllegalArgumentException(String.format(
								"Request %s failed, cannot parse response: %s",
								request, response.body().string()
							), e));
						}
					} else {
						final var code = response.code();
						final var body = response.body().string();
						logRequestFailed(newRequestId, newRequestTime, code, body);
						emitter.onError(new IllegalArgumentException(String.format(
							"Request %s failed with code %d: %s",
							request, code, body
						)));
					}
				}
			});
		});
	}

	private void logRequestSuccessful(int requestId, long requestTime, String body) {
		final var requestDuration = System.currentTimeMillis() - requestTime;
		log.info("Request {} ({}ms): successful {}", requestId, requestDuration, body);
	}

	private void logRequestFailed(int requestId, long requestTime, int code, String body) {
		final var requestDuration = System.currentTimeMillis() - requestTime;
		log.error("Request {} ({}ms): failed with code {} ({})", requestId, requestDuration, code, body);
	}

	private void logRequestFailed(int requestId, long requestTime, IOException e) {
		final var requestDuration = System.currentTimeMillis() - requestTime;
		log.error(String.format("Request %s (%sms): failed with exception", requestId, requestDuration), e);
	}

	/**
	 * Queries the specified endpoint of the given node when subscribed to.
	 *
	 * @param nodeId   The node identifier to query
	 * @param endpoint The endpoint to query at that node
	 * @return A cold {@link Single}, executing the query once when subscribed to
	 */
	public Single<String> queryEndpoint(String nodeId, String endpoint) {
		Objects.requireNonNull(nodeId, "nodeId");
		Objects.requireNonNull(endpoint, "endpoint");

		Request request = new Request.Builder().url(this.network.getEndpointUrl(nodeId, endpoint)).build();
		return makeRequest(request);
	}

	/**
	 * Queries the specified endpoint of the given node and interprets the result as a {@link JSONObject}
	 *
	 * @param nodeId   The node identifier to query
	 * @param endpoint The endpoint to query at that node
	 * @return A cold {@link Single}, executing the query once when subscribed to
	 */
	public Single<JSONObject> queryEndpointJson(String nodeId, String endpoint) {
		return queryEndpoint(nodeId, endpoint)
			.map(JSONObject::new);
	}

	/**
	 * Starts the consensus process in all nodes in an idempotent way when subscribed to.
	 *
	 * @return A cold {@link Completable} that completes when all requests were successful
	 */
	public Completable startConsensus() {
		return Completable.mergeDelayError(
			getNodeIds().stream()
				.map(this::startConsensus)
				.collect(Collectors.toList())
		);
	}

	/**
	 * Starts the consensus process in a specified node when subscribed to.
	 * @param nodeId The node
	 * @return A cold {@link Completable} that completes when the request was successful
	 */
	public Completable startConsensus(String nodeId) {
		JSONObject jsonRpcStartRequest = new JSONObject();
		jsonRpcStartRequest.put("id", UUID.randomUUID());
		jsonRpcStartRequest.put("method", "BFT.start");
		jsonRpcStartRequest.put("params", new JSONObject());
		Request startRequest = new Request.Builder()
			.url(network.getEndpointUrl(nodeId, "rpc"))
			.method("POST", RequestBody.create(null, jsonRpcStartRequest.toString()))
			.build();
		return makeRequest(startRequest)
			.ignoreElement();
	}

	/**
	 * Gets the node identifiers in the underlying network
	 *
	 * @return The node identifiers
	 */
	public Set<String> getNodeIds() {
		return this.network.getNodeIds();
	}

	/**
	 * Gets the number of nodes in the underlying network
	 *
	 * @return The number of ndoes
	 */
	public int getNumNodes() {
		return this.network.getNodeIds().size();
	}

	/**
	 * Creates a network bridge encapsulating a certain {@link RemoteBFTNetwork} implementation.
	 *
	 * @param network The network to bridge
	 * @return A bridge to the given network
	 */
	public static RemoteBFTNetworkBridge of(RemoteBFTNetwork network) {
		return new RemoteBFTNetworkBridge(network);
	}
}
