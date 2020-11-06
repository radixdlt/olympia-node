package com.radixdlt.test;


import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import utils.CmdHelper;
import utils.Generic;
import utils.SlowNodeSetup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 * BFT tests against network where all nodes are under synchrony bounds and one or more nodes slow.
 */
@RunWith(Enclosed.class)
public class SlowNodeTest {
	private static final Logger logger = LogManager.getLogger();

	@RunWith(Parameterized.class)
	@Category(Docker.class)
	public static class DockerTests {

		private int networkSize;
		@Rule
		public TestName name = new TestName();

		public DockerTests(int networkSize) {
			this.networkSize = networkSize;
		}

		@Parameters(name = "{index}: given_{0}_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{{3}, {4}});
		}

		@Test
		public void tests() {
			String name = Generic.extractTestName(this.name.getMethodName());
			logger.info("Test name is " + name);
			try (DockerNetwork network = DockerNetwork.builder().numNodes(networkSize).testName(name).build()) {
				network.startBlocking();
				String veth = CmdHelper.getVethByContainerName(network.getNodeIds().stream().findFirst().get());
				CmdHelper.setupQueueQuality(veth,"delay 100ms loss 20%");

				RemoteBFTTest test = AssertionChecks
					.slowNodeTestBuilder()
					.network(RemoteBFTNetworkBridge.of(network))
					.waitUntilResponsive()
					.startConsensusOnRun().build();
				test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
			}
		}
	}


	@Category(Cluster.class)
	public static class ClusterTests {
		private SlowNodeSetup slowNodeSetup;
		private StaticClusterNetwork network;

		@Before
		public void setupSlowNode() {
			network = StaticClusterNetwork.clusterInfo(10);
			String sshKeylocation = Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa");

			//Creating named volume and copying over the file to volume works with or without docker in docker setup
			slowNodeSetup = SlowNodeSetup.builder()
				.withImage("eu.gcr.io/lunar-arc-236318/node-ansible:python3")
				.nodesToSlowDown(1)
				.usingCluster(network.getClusterName())
				.runOptions("--rm -v key-volume:/ansible/ssh --name node-ansible")
				.build();
			slowNodeSetup.copyfileToNamedVolume(sshKeylocation,"key-volume");
			slowNodeSetup.pullImage();
			slowNodeSetup.setup();
		}

		@Test
		public void given_10_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made() {

			RemoteBFTTest test = AssertionChecks.slowNodeTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun() // in case we're the first to access the cluster
				.build();
			test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
		}

		@After
		public void removeSlowNodesettings(){
			slowNodeSetup.tearDown();
		}
	}

}
