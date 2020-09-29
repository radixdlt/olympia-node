package com.radixdlt.test;

import static com.radixdlt.test.AssertionChecks.*;

import com.radixdlt.test.AssertionChecks;
import com.radixdlt.test.Docker;
import com.radixdlt.test.DockerNetwork;
import com.radixdlt.test.RemoteBFTNetworkBridge;
import com.radixdlt.test.RemoteBFTTest;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.C;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import utils.CmdHelper;
import utils.Generic;
import utils.SlowNodeSetup;

@RunWith(Enclosed.class)
public class OutOfSynchronyBoundsTest {


	private static final Logger logger = LogManager.getLogger();

	@RunWith(Parameterized.class)
	@Category(Docker.class)
	public static class DockerTests {

		@Rule
		public TestName name = new TestName();

		@Test
		public void given_4_correct_bfts_in_latent_docker_network__when_one_instance_is_down__then_all_instances_should_get_same_commits_and_progress_should_be_made() {

			String name = Generic.extractTestName(this.name.getMethodName());
			logger.info("Test name is " + name);
			try (DockerNetwork network = DockerNetwork.builder().numNodes(4).testName(name).startConsensusOnBoot().build()) {
				network.startBlocking();

				RemoteBFTTest test = slowNodeTestBuilder().network(RemoteBFTNetworkBridge.of(network)).waitUntilResponsive().build();
				test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);

				String nodeNetworkSlowed = network.getNodeIds().stream().findFirst().get();
				String veth = CmdHelper.getVethByContainerName(nodeNetworkSlowed);
				CmdHelper.setupQueueQuality(veth, "delay 100ms loss 100%");

				ArrayList<String> setNodesToIgnore = new ArrayList<String>();
				setNodesToIgnore.add(nodeNetworkSlowed);

				RemoteBFTTest testOutOfSynchronyBounds = outOfSynchronyTestBuilder(setNodesToIgnore).network(RemoteBFTNetworkBridge.of(network)).build();

				testOutOfSynchronyBounds.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);

			}

		}
	}


	@Category(Cluster.class)
	public static class ClusterTests {

		private SlowNodeSetup slowNodeSetup;
		private StaticClusterNetwork network;

		@Rule
		public TestName name = new TestName();


		@Before
		public void setupSlowNode() {
			network = StaticClusterNetwork.clusterInfo(4);
			String sshKeylocation = Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa");

			//Creating named volume and copying over the file to volume works with or without docker in docker setup
			slowNodeSetup = SlowNodeSetup.builder()
				.withAnsibleImage("eu.gcr.io/lunar-arc-236318/node-ansible:latest")
				.withKeyVolume("key-volume")
				.usingCluster(network.getClusterName())
				.usingAnsiblePlaybook("slow-down-node.yml")
				.nodesToSlowDown(1)
				.build();
			slowNodeSetup.copyfileToNamedVolume(sshKeylocation);
			slowNodeSetup.pullImage();
			slowNodeSetup.setup();
		}


		@Test
		@Category(Cluster.class)
		public void given_10_correct_bfts_in_cluster_network__when_one_node_is_down__then_all_other_instances_should_get_same_commits_and_progress_should_be_made() {
			String name = Generic.extractTestName(this.name.getMethodName());
			logger.info("Test name is " + name);
		}

		@After
		public void removeSlowNodesettings(){
			slowNodeSetup.tearDown();
		}
	}
}
