package com.radixdlt.test;

import static com.radixdlt.test.AssertionChecks.*;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import utils.EphemeralNetworkCreator;
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

		private EphemeralNetworkCreator ephemeralNetworkCreator;
		private StaticClusterNetwork network;

		@Rule
		public TestName name = new TestName();


		@Before
		public void setupCluster() {
			String SSH_IDENTITY = Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa");
			String SSH_IDENTITY_PUB = Optional.ofNullable(System.getenv("SSH_IDENTITY_PUB")).orElse(System.getenv("HOME") + "/.ssh/id_rsa.pub");
			String AWS_CREDENTIAL = System.getenv("AWS_CREDENTIAL");
			String GCP_CREDENTIAL = System.getenv("GCP_CREDENTIAL");
			String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
			String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");

			//Creating named volume and copying over the file to volume works with or without docker in docker setup
			ephemeralNetworkCreator = EphemeralNetworkCreator.builder()
				.withTerraformImage("eu.gcr.io/lunar-arc-236318/node-terraform:latest")
				.withKeyVolume("key-volume")
				.withTotalNumofNodes(10)
				.withTerraformOptions(Collections.emptyList() )
				.build();
			ephemeralNetworkCreator.copyfileToNamedVolume(SSH_IDENTITY);
			ephemeralNetworkCreator.copyfileToNamedVolume(SSH_IDENTITY_PUB,"/terraform/ssh","testnet.pub");
			ephemeralNetworkCreator.copyfileToNamedVolume(AWS_CREDENTIAL, "/terraform/ssh","aws-env-file.sh");
			ephemeralNetworkCreator.copyfileToNamedVolume(AWS_CREDENTIAL,"/ansible/ssh","aws-env-file.sh");
			ephemeralNetworkCreator.copyfileToNamedVolume(GCP_CREDENTIAL,
				"/ansible/ssh","dev-container-repo-uploader.json");
			ephemeralNetworkCreator.pullImage();
			ephemeralNetworkCreator.plan();
			ephemeralNetworkCreator.setup();
			ephemeralNetworkCreator.nodesToBringdown("tag_Environment_ephemeralnet");
			ephemeralNetworkCreator.deploy();

			network = ephemeralNetworkCreator.getNetwork(10);


			RemoteBFTTest test = AssertionChecks.slowNodeTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);

			String sshKeylocation = Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa");

			String nodeURL = network.getNodeIds().iterator().next();
			String nodeToBringdown = Generic.getDomainName(nodeURL);

			ephemeralNetworkCreator.nodesToBringdown(nodeToBringdown);

			ArrayList<String> setNodesToIgnore = new ArrayList<String>();
			setNodesToIgnore.add(nodeURL);
			RemoteBFTTest testOutOfSynchronyBounds = outOfSynchronyTestBuilder(setNodesToIgnore).network(RemoteBFTNetworkBridge.of(network)).build();
			testOutOfSynchronyBounds.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
		}


		@Test
		@Category(Cluster.class)
		public void given_10_correct_bfts_in_cluster_network__when_one_node_is_down__then_all_other_instances_should_get_same_commits_and_progress_should_be_made() {
			String name = Generic.extractTestName(this.name.getMethodName());
			logger.info("Test name is " + name);
		}

		@After
		public void removeSlowNodesettings(){
			ephemeralNetworkCreator.teardown();
		}
	}
}
