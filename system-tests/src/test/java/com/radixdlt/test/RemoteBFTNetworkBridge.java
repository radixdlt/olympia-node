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
import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * A bridge between a test and a certain {@link RemoteBFTNetwork} implementation,
 * providing utility methods to operate on remote networks while abstracting away the details of the network.
 */
public final class RemoteBFTNetworkBridge {
	private final RemoteBFTNetwork network;

	private RemoteBFTNetworkBridge(RemoteBFTNetwork network) {
		this.network = network;
	}

	/**
	 * Queries the specified endpoint of the given node when subscribed to.
	 * @param nodeId The node identifier to query
	 * @param endpoint The endpoint to query at that node
	 * @return A cold {@link Single}, executing the query once when subscribed to
	 */
	public Single<String> queryEndpoint(String nodeId, String endpoint) {
		Objects.requireNonNull(nodeId, "nodeId");
		Objects.requireNonNull(endpoint, "endpoint");

		SingleSubject<String> responseSubject = SingleSubject.create();
		return responseSubject.doOnSubscribe(x -> {
			Request request = new Request.Builder().url(this.network.getEndpointUrl(nodeId, endpoint)).build();
			Call call = HttpClients.getSslAllTrustingClient().newCall(request);
			call.enqueue(new Callback() {
				@Override
				public void onFailure(Call call, IOException e) {
					responseSubject.onError(e);
				}

				@Override
				public void onResponse(Call call, Response response) throws IOException {
					try {
						String responseString = response.body().string();
						responseSubject.onSuccess(responseString);
					} catch (IOException e) {
						responseSubject.onError(new IllegalArgumentException("Failed to parse response to " + request, e));
					}
				}
			});
		});
	}

	/**
	 * Queries the specified endpoint of the given node and interprets the result as a {@link JSONObject}
	 * @param nodeId The node identifier to query
	 * @param endpoint The endpoint to query at that node
	 * @return A cold {@link Single}, executing the query once when subscribed to
	 */
	public Single<JSONObject> queryEndpointJson(String nodeId, String endpoint) {
		return queryEndpoint(nodeId, endpoint)
			.map(JSONObject::new);
	}

	/**
	 * Gets the node identifiers in the underlying network
	 * @return The node identifiers
	 */
	public Set<String> getNodeIds() {
		return this.network.getNodeIds();
	}

	/**
	 * Gets the number of nodes in the underlying network
	 * @return The number of ndoes
	 */
	public int getNumNodes() {
		return this.network.getNodeIds().size();
	}

	/**
	 * Creates a network bridge encapsulating a certain {@link RemoteBFTNetwork} implementation.
	 * @param network The network to bridge
	 * @return A bridge to the given network
	 */
	public static RemoteBFTNetworkBridge of(RemoteBFTNetwork network) {
		return new RemoteBFTNetworkBridge(network);
	}
}
