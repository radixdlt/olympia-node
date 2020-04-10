package com.radixdlt.test;

import io.reactivex.Observable;
import org.junit.Test;
import utils.CmdHelper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static utils.CmdHelper.node;
import static utils.CmdHelper.runCommand;

public class LatentDockerNetworkTest {

	// copied from ValidatorSet in core
	static int acceptableFaults(int n) {
		// Compute acceptable faults based on Byzantine limit n = 3f + 1
		// i.e. f = (n - 1) / 3
		return (n - 1) / 3;
	}

	@Test
	public void given_4_correct_bfts_in_latent_network__then_all_instances_should_get_same_commits_consecutive_vertices_eventually_over_1_minute() {
		final int numNodes = 4;
		final int quorumSize = numNodes - acceptableFaults(numNodes);
		final long runtimeSeconds = 1;
		final long refreshIntervalMillis = 500;
		final TimeUnit timeUnit = TimeUnit.MINUTES;
		final String networkName = "junit_test";
		Map<String, Map<String, Object>> dockerOptionsPerNode = CmdHelper.getDockerOptions(numNodes, quorumSize);
		CmdHelper.removeAllDockerContainers();
		CmdHelper.runCommand("docker network rm " + networkName);
		CmdHelper.runCommand("docker network create " + networkName ,null, true);

		dockerOptionsPerNode.forEach((nodeName, options) -> {
			options.put("network",networkName);
			List<Object> dockerSetup = CmdHelper.node(options);
			String[] dockerEnv = (String[]) dockerSetup.get(0);
			String dockerCommand = (String) dockerSetup.get(1);
			CmdHelper.runCommand(dockerCommand, dockerEnv,true);
		});

		CmdHelper.checkNGenerateKey();


		// TODO fetch consensus data from each node and check every refreshInterval until runtimeSeconds is over
		// see LatentNetwork integration test in core
//		dockerOptionsPerNode.keySet().stream()
//			.map(nodeName -> Observable.interval(refreshIntervalMillis, TimeUnit.MILLISECONDS)
//				.map(i -> ))


	}
}
