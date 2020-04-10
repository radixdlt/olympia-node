package com.radixdlt.test;

import com.radixdlt.client.core.network.HttpClients;
import com.sun.tools.javac.util.Pair;
import io.reactivex.Observable;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.junit.Test;
import utils.CmdHelper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertThat;

public class LatentDockerNetworkTest {
	private static final String OPTIONS_KEY_NAME = "nodeName";
	private static final String OPTIONS_KEY_PORT = "hostPort";

	// utility for getting the API endpoint (as a string) out of generated node options
	private String extractHttpApiSystemEndpoint(Map<String, Object> nodeOptions) {
		String nodeName = (String) nodeOptions.get(OPTIONS_KEY_NAME);
		int nodePort = Integer.parseInt((String) nodeOptions.get(OPTIONS_KEY_PORT));

		return String.format("https://%s:%d/api/system", nodeName, nodePort);
	}

	// utility for fetching the consensus counter values through the node api given a certain request
	private Map<String, Integer> fetchConsensusCounters(Request request) {
		Map<String, Integer> consensusCounters;
		Call call = HttpClients.getSslAllTrustingClient().newCall(request);
		try (Response response = call.execute()) {
			String responseString = response.body().string();
			JSONObject responseJson = new JSONObject(responseString);
			JSONObject countersJson = responseJson.getJSONObject("counters");
			JSONObject consensusCountersJson = countersJson.getJSONObject("consensus");
			consensusCounters = consensusCountersJson.keySet().stream()
				.collect(Collectors.toMap(c -> c, consensusCountersJson::getInt));

		} catch (IOException e) {
			throw new IllegalStateException("Failed to get response to " + request, e);
		}
		return consensusCounters;
	}

	@Test
	public void given_4_correct_bfts_in_latent_network__then_all_instances_should_get_same_commits_consecutive_vertices_eventually_over_1_minute() {
		final int numNodes = 4;
		final long runtimeSeconds = 1;
		final long refreshIntervalMillis = 500;
		final TimeUnit timeUnit = TimeUnit.MINUTES;
		final String networkName = "junit_test";

		// set up the network
		Map<String, Map<String, Object>> dockerOptionsPerNode = CmdHelper.getDockerOptions(numNodes, numNodes);
		CmdHelper.removeAllDockerContainers();
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

		// precreate api requests so we can just re-use the same request objects for pinging each node's API
		Map<String, Request> apiRequestsPerNode = dockerOptionsPerNode.values().stream()
			.collect(Collectors.toMap(
				nodeOptions -> (String) nodeOptions.get(OPTIONS_KEY_NAME),
				nodeOptions -> new Request.Builder().url(extractHttpApiSystemEndpoint(nodeOptions)).build())
			);

		// perform actual assertions based on the responses we get to periodic api requests
		// TODO fetch consensus data from each node and check every refreshInterval until runtimeSeconds is over
		// see LatentNetwork integration test in core
		Observable<Map<String, Map<String, Integer>>> consensusCountersPerNode = Observable.interval(refreshIntervalMillis, TimeUnit.MILLISECONDS)
			.map(i -> dockerOptionsPerNode.keySet().stream()
				// fetchConsensusCounters is blocking, so we might want to do this async instead (especially for higher numbers of nodes)
				.collect(Collectors.toMap(n -> n, nodeName -> fetchConsensusCounters(apiRequestsPerNode.get(nodeName)))));
		consensusCountersPerNode
			.map(Map::values) // TODO don't drop node names here for better error reporting down in the assertion
			.map(allCounters -> allCounters.stream()
					.map(counters -> counters.get("view"))
					.distinct()
					.collect(Collectors.toList()));
		// TODO assert that the views are all good (how?)
	}
}
