package com.radixdlt.test;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class LatentDockerNetworkTest {
	@Test
	public void given_3_correct_bfts_in_latent_network__then_all_instances_should_get_same_commits_consecutive_vertices_eventually_over_1_minute() {
		final int numNodes = 3;
		try (DockerBFTTestNetwork network = DockerBFTTestNetwork.builder()
				.numNodes(numNodes)
				.build()) {
			RemoteBFTTest test = new RemoteBFTTest(network);
			test.run(1, TimeUnit.MINUTES);
		}
	}
}
