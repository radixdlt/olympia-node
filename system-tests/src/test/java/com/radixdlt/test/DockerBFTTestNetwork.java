package com.radixdlt.test;

import com.radixdlt.client.core.network.HttpClients;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DockerBFTTestNetwork implements Closeable {
	private static final String OPTIONS_KEY_NAME = "nodeName";
	private static final String OPTIONS_KEY_PORT = "hostPort";

	private final int numNodes;
	private final String networkName;
	private Map<String, Request> preparedApiRequests; // api requests by node name

	private DockerBFTTestNetwork(String networkName, int numNodes) {
		this.networkName = Objects.requireNonNull(networkName);
		this.numNodes = numNodes;

		this.setup();
	}

	// setup the network and prepare anything required to run it
	// this will also kill any other network with the same name (but not networks with a different name)
	private void setup() {
		Map<String, Map<String, Object>> dockerOptionsPerNode = CmdHelper.getDockerOptions(numNodes, numNodes);
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

		// prepare the api requests for use in checks
		this.preparedApiRequests = dockerOptionsPerNode.values().stream()
			.collect(Collectors.toMap(
				nodeOptions -> (String) nodeOptions.get(OPTIONS_KEY_NAME),
				nodeOptions -> new Request.Builder().url(extractHttpApiSystemEndpoint(nodeOptions)).build())
			);
	}

	@Override
	public void close() {
		CmdHelper.runCommand("docker network rm " + networkName);
	}

	// TODO revisit, might want to ping other node APIs (wouldn't be hard to generalise)
	public Single<JSONObject> fetchSystem(String nodeName) {
		Request request = this.preparedApiRequests.get(nodeName);
		if (request == null) {
			throw new IllegalArgumentException("Unknown node " + nodeName);
		}

		SingleSubject<JSONObject> responseSubject = SingleSubject.create();
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
					JSONObject responseJson = new JSONObject(responseString);
					responseSubject.onSuccess(responseJson);
				} catch (IOException e) {
					responseSubject.onError(new IllegalArgumentException("Failed to parse response to " + request, e));
				}
			}
		});
		return responseSubject;
	}

	// TODO document and revisit
	// utility for fetching the consensus counter values through the node api given a certain request
	public Single<Map<String, Integer>> fetchConsensusCounters(String nodeName) {
		return fetchSystem(nodeName)
			.map(json -> json.getJSONObject("counters"))
			.map(counters -> counters.getJSONObject("consensus"))
			.map(consensusCounters -> consensusCounters.keySet().stream()
				.collect(Collectors.toMap(c -> c, consensusCounters::getInt)));
	}

	// utility for getting the API endpoint (as a string) out of generated node options
	private static String extractHttpApiSystemEndpoint(Map<String, Object> nodeOptions) {
		String nodeName = (String) nodeOptions.get(OPTIONS_KEY_NAME);
		int nodePort = (Integer) nodeOptions.get(OPTIONS_KEY_PORT);

		return String.format("https://%s:%d/api/system", nodeName, nodePort);
	}

	public Set<String> getNodeNames() {
		return this.preparedApiRequests.keySet();
	}

	public String getNetworkName() {
		return networkName;
	}

	public int getNumNodes() {
		return numNodes;
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
