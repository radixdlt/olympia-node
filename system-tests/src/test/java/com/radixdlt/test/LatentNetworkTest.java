package com.radixdlt.test;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

/**
 * BFT tests against static, non-byzantine networks with random latency.
 */
public class LatentNetworkTest {

	/**
	 * Gets the test builder for latent BFT network tests.
	 *
	 * @return The test builder
	 */
	static RemoteBFTTest.Builder latentTestBuilder() {
		return RemoteBFTTest.builder().assertResponsiveness().assertAllProposalsHaveDirectParents().assertNoRejectedProposals()
			.assertNoSyncExceptions().assertNoTimeouts().assertSafety().assertLiveness();
	}

	@Test
	@Category(Docker.class)
	public void given_3_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		try (DockerNetwork network = DockerNetwork.builder().numNodes(3).build()) {
			network.startBlocking();
			RemoteBFTTest test = latentTestBuilder().network(RemoteBFTNetworkBridge.of(network)).waitUntilResponsive().startConsensusOnRun().build();
			test.runBlocking(1, TimeUnit.MINUTES);
		}
	}

	@Test
	@Category(Docker.class)
	public void given_4_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		try (DockerNetwork network = DockerNetwork.builder().numNodes(4).build()) {
			network.startBlocking();
			RemoteBFTTest test = latentTestBuilder().network(RemoteBFTNetworkBridge.of(network)).waitUntilResponsive().startConsensusOnRun().build();
			test.runBlocking(1, TimeUnit.MINUTES);
		}
	}

	@Test
	@Category(Cluster.class)
	public void given_4_correct_bfts_in_latent_cluster_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		final StaticClusterNetwork network = StaticClusterNetwork.extractFromProperty(4);
		RemoteBFTTest test = latentTestBuilder().network(RemoteBFTNetworkBridge.of(network)).waitUntilResponsive()
			.startConsensusOnRun() // in case we're the first to access the cluster
			.build();
		test.runBlocking(1, TimeUnit.MINUTES);
	}
}
