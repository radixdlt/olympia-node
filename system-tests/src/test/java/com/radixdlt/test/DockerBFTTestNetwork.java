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
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import utils.CmdHelper;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DockerBFTTestNetwork implements Closeable {
	private static final String OPTIONS_KEY_NAME = "nodeName";
	private static final String OPTIONS_KEY_PORT = "hostPort";

	private final int numNodes;
	private final String networkName;
	private final Map<String, Map<String, Object>> dockerOptionsPerNode;

	private DockerBFTTestNetwork(String networkName, int numNodes) {
		this.networkName = Objects.requireNonNull(networkName);
		this.numNodes = numNodes;

		this.dockerOptionsPerNode = this.setup();
	}

	// setup the network and prepare anything required to run it
	// this will also kill any other network with the same name (but not networks with a different name)
	private Map<String, Map<String, Object>> setup() {
		Map<String, Map<String, Object>> dockerOptionsPerNode = CmdHelper.getDockerOptions(numNodes, numNodes);
		CmdHelper.removeAllDockerContainers(); // TODO do we need this? if yes, document it
		CmdHelper.runCommand("docker network rm " + networkName);
		CmdHelper.runCommand("docker network create " + networkName ,null, true);
		dockerOptionsPerNode.forEach((nodeName, options) -> {
			options.put("network", networkName);
			List<Object> dockerSetup = CmdHelper.node(options);
			String[] dockerEnv = (String[]) dockerSetup.get(0);
			String dockerCommand = (String) dockerSetup.get(1);
			CmdHelper.runCommand(dockerCommand, dockerEnv,true);
		});
		CmdHelper.checkNGenerateKey();

		return Collections.unmodifiableMap(dockerOptionsPerNode);
	}

	@Override
	public void close() {
		CmdHelper.removeAllDockerContainers();
		CmdHelper.runCommand("docker network rm " + networkName);
	}

	public Single<String> query(String nodeName, String endpoint) {
		Objects.requireNonNull(nodeName, "nodeName");
		Objects.requireNonNull(endpoint, "endpoint");

		Request request = new Request.Builder().url(getNodeEndpoint(nodeName, endpoint)).build();
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

	public Single<JSONObject> queryJson(String nodeName, String endpoint) {
		return query(nodeName, endpoint)
			.map(JSONObject::new);
	}

	private String getNodeEndpoint(String nodeName, String endpoint) {
		return getNodeEndpoint(this.dockerOptionsPerNode.get(nodeName), endpoint);
	}

	// utility for getting the API endpoint (as a string) out of generated node options
	private static String getNodeEndpoint(Map<String, Object> nodeOptions, final String endpoint) {
		int nodePort = (Integer) nodeOptions.get(OPTIONS_KEY_PORT);

		return String.format("http://localhost:%d/%s", nodePort, endpoint);
	}

	public Set<String> getNodeNames() {
		return this.dockerOptionsPerNode.keySet();
	}

	public String getNetworkName() {
		return this.networkName;
	}

	public int getNumNodes() {
		return this.numNodes;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private static AtomicInteger networkIdCounter = new AtomicInteger(0);
		private String name = "test-network-" + networkIdCounter.getAndIncrement();
		private int numNodes = -1;

		public Builder name(String name) {
			this.name = Objects.requireNonNull(name);
			return this;
		}

		public Builder numNodes(int numNodes) {
			if (numNodes < 1) {
				throw new IllegalArgumentException("numNodes must be >= 1 but was " + numNodes);
			}
			this.numNodes = numNodes;
			return this;
		}

		public DockerBFTTestNetwork build() {
			if (numNodes == -1) {
				throw new IllegalStateException("numNodes was not set");
			}

			return new DockerBFTTestNetwork(name, numNodes);
		}
	}
}
