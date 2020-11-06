package com.radixdlt.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;
import org.junit.rules.TestName;
import utils.CmdHelper;
import utils.Generic;

/**
 * BFT tests against static, non-byzantine networks with random latency.
 */
public class LatentNetworkTest {
	private static final Logger logger = LogManager.getLogger();

	@Rule
	public TestName name = new TestName();

	@Test
	@Category(Docker.class)
	public void given_3_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		String name = Generic.extractTestName(this.name.getMethodName());
		logger.info("Test name is " + Generic.extractTestName(this.name.getMethodName()));
		try (DockerNetwork network = DockerNetwork.builder().numNodes(3).testName(name).build()) {
			network.startBlocking();
			RemoteBFTTest test = AssertionChecks.latentTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
		}
	}

	@Test
	@Category(Docker.class)
	public void given_4_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		String name = Generic.extractTestName(this.name.getMethodName());
		logger.info("Test name is " + name);

		try (DockerNetwork network = DockerNetwork.builder().numNodes(4).testName(name).build()) {
			network.startBlocking();
			RemoteBFTTest test = AssertionChecks.latentTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
		}
	}

	@Test
	@Category(Cluster.class)
	public void given_10_correct_bfts_in_latent_cluster_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		final StaticClusterNetwork network = StaticClusterNetwork.clusterInfo(10);
		RemoteBFTTest test = AssertionChecks.latentTestBuilder()
			.network(RemoteBFTNetworkBridge.of(network))
			.waitUntilResponsive()
			.startConsensusOnRun() // in case we're the first to access the cluster
			.build();
		test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
	}
}
