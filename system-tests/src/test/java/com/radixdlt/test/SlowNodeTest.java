package com.radixdlt.test;


import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import utils.CmdHelper;
import utils.SlowNodeSetup;
import utils.TestnetNodes;

/**
 * BFT tests against network where all nodes are under synchrony bounds and one or more nodes slow.
 */
@RunWith(Enclosed.class)
public class SlowNodeTest {


	@RunWith(Parameterized.class)
	@Category(Docker.class)
	public static class DockerTests {

		private int networkSize;

		public DockerTests(int networkSize) {
			this.networkSize = networkSize;
		}

		@Parameters(name = "{index}: given_{0}_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{{3}, {4}});
		}

		@Test
		public void tests() {
			try (DockerNetwork network = DockerNetwork.builder().numNodes(networkSize).build()) {

				network.startBlocking();
				String veth = CmdHelper.getVethByContainerName(network.getNodeIds().stream().findFirst().get());
				CmdHelper.setupQueueQuality(veth);

				RemoteBFTTest test = AssertionChecks
					.slowNodeTestBuilder()
					.network(RemoteBFTNetworkBridge.of(network))
					.waitUntilResponsive()
					.startConsensusOnRun().build();
				test.runBlocking(1, TimeUnit.MINUTES);
			}
		}
	}


	@Category(Cluster.class)
	public static class ClusterTests {
		private SlowNodeSetup slowNodeSetup;

		@Before
		public void setupSlowNode() {

			System.setProperty("clusterNodeUrls",TestnetNodes.getInstance().getNodesURls());
			String sshKeylocation = Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa");
			//Creating named volume and copying over the file to volume works with or without docker in docker setup
			slowNodeSetup = SlowNodeSetup.builder()
				.withAnsibleImage("eu.gcr.io/lunar-arc-236318/node-ansible:latest")
				.withKeyVolume("key-volume")
				.usingCluster("testnet_2")
				.usingAnsiblePlaybook("slow-down-node.yml")
				.nodesToSlowDown(1)
				.build();
			slowNodeSetup.copyfileToNamedVolume(sshKeylocation);
			slowNodeSetup.pullImage();
			slowNodeSetup.setup();

		}

		@Test
		public void given_4_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made() {

			final StaticClusterNetwork network = StaticClusterNetwork.extractFromProperty(4);
			RemoteBFTTest test = AssertionChecks.slowNodeTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun() // in case we're the first to access the cluster
				.build();
			test.runBlocking(1, TimeUnit.MINUTES);
		}

		@After
		public void removeSlowNodesettings(){
			slowNodeSetup.tearDown();
		}
	}

}
