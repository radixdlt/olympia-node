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

public final class RemoteBFTNetworkBridge {
	private final BFTNetwork network;

	private RemoteBFTNetworkBridge(BFTNetwork network) {
		this.network = network;
	}

	public Single<String> queryEndpoint(String nodeId, String endpoint) {
		Objects.requireNonNull(nodeId, "nodeId");
		Objects.requireNonNull(endpoint, "endpoint");

		Request request = network.makeRequest(nodeId, endpoint);
		SingleSubject<String> responseSubject = SingleSubject.create();
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
		return responseSubject;
	}

	public Single<JSONObject> queryEndpointJson(String nodeId, String endpoint) {
		return queryEndpoint(nodeId, endpoint)
			.map(JSONObject::new);
	}

	public Set<String> getNodeIds() {
		return this.network.getNodeIds();
	}

	public int getNumNodes() {
		return this.network.getNodeIds().size();
	}

	public static RemoteBFTNetworkBridge of(BFTNetwork network) {
		return new RemoteBFTNetworkBridge(network);
	}
}
