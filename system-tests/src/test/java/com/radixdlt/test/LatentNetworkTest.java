package com.radixdlt.test;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;
import utils.CmdHelper;
import utils.TestnetNodes;

/**
 * BFT tests against static, non-byzantine networks with random latency.
 */
public class LatentNetworkTest {

	@Test
	@Category(Docker.class)
	public void given_3_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		try (DockerNetwork network = DockerNetwork.builder().numNodes(3).build()) {
			network.startBlocking();
			RemoteBFTTest test = AssertionChecks.latentTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(CmdHelper.getATestDurationInMin(), TimeUnit.MINUTES);
		}
	}

	@Test
	@Category(Docker.class)
	public void given_4_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		try (DockerNetwork network = DockerNetwork.builder().numNodes(4).build()) {
			network.startBlocking();
			RemoteBFTTest test = AssertionChecks.latentTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(CmdHelper.getATestDurationInMin(), TimeUnit.MINUTES);
		}
	}

	@Test
	@Category(Cluster.class)
	public void given_4_correct_bfts_in_latent_cluster_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		final StaticClusterNetwork network = StaticClusterNetwork.clusterInfo(4);
		RemoteBFTTest test = AssertionChecks.latentTestBuilder()
			.network(RemoteBFTNetworkBridge.of(network))
			.waitUntilResponsive()
			.startConsensusOnRun() // in case we're the first to access the cluster
			.build();
		test.runBlocking(CmdHelper.getATestDurationInMin(), TimeUnit.MINUTES);
	}
}
