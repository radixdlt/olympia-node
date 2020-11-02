package com.radixdlt.test;

import static com.radixdlt.test.AssertionChecks.*;
import static java.util.stream.Collectors.toList;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.junit.runners.Parameterized.Parameters;
import utils.Constants;
import utils.CmdHelper;
import utils.EphemeralNetworkCreator;
import utils.Generic;

@RunWith(Enclosed.class)
public class OutOfSynchronyBoundsTest {


	private static final Logger logger = LogManager.getLogger();

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

				RemoteBFTTest testOutOfSynchronyBounds = outOfSynchronyTestBuilder(setNodesToIgnore)
					.network(RemoteBFTNetworkBridge.of(network))
					.build();

				testOutOfSynchronyBounds.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);

			}

		}
	}


	@Category(EphemeralCluster.class)
	@RunWith(Parameterized.class)
	public static class ClusterTests {

		private EphemeralNetworkCreator ephemeralNetworkCreator;
		private StaticClusterNetwork network;

		@Rule
		public TestName name = new TestName();

		private int networkSize,nodesCrashed;
		List<String> crashedNodesURLs;

		public ClusterTests(int networkSize,int nodesCrashed) {
			this.networkSize = networkSize;
			this.nodesCrashed = nodesCrashed;
		}

		@Parameters(name = "{index}: given_{0}_correct_bfts_in_cluster_network__when_{1}_node_is_down__then_all_other_instances_should_get_same_commits_and_progress_should_be_made")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{
				{10,1},
				{10,3},
				{4,1}
			});
		}

		@Before
		public void setupCluster() {
			String SSH_IDENTITY = Optional.ofNullable(System.getenv(EphemeralNetworkCreator.ENV_SSH_IDENTITY))
				.orElse(System.getenv("HOME") + "/.ssh/id_rsa");
			String SSH_IDENTITY_PUB = Optional.ofNullable(System.getenv(EphemeralNetworkCreator.ENV_SSH_IDENTITY_PUB))
				.orElse(System.getenv("HOME") + "/.ssh/id_rsa.pub");
			String AWS_CREDENTIAL = System.getenv(EphemeralNetworkCreator.ENV_AWS_CREDENTIAL);
			String GCP_CREDENTIAL = System.getenv(EphemeralNetworkCreator.ENV_GCP_CREDENTIAL);
			String CREDENTIAL_VOL_NAME = Optional.ofNullable(System.getenv(Constants.getCREDENTIAL_VOL_NAME()))
				.orElse("key-volume");


			ephemeralNetworkCreator = EphemeralNetworkCreator.builder()
				.withTerraformImage("eu.gcr.io/lunar-arc-236318/node-terraform:latest")
				.withAnsibleImage("eu.gcr.io/lunar-arc-236318/node-ansible:python3")
				.withKeyVolume(CREDENTIAL_VOL_NAME)
				.withTerraformOptions(Collections.emptyList()).build();

			ephemeralNetworkCreator.copyToTFSecrets(SSH_IDENTITY);
			ephemeralNetworkCreator.copyToTFSecrets(SSH_IDENTITY_PUB,  Constants.getTESTNET_SSH_KEY_FILE_NAME());
			ephemeralNetworkCreator.copyToTFSecrets(AWS_CREDENTIAL,  Constants.getAWS_CREDENTIAL_FILE());

			ephemeralNetworkCreator.copyToAnsibleSecrets(AWS_CREDENTIAL, Constants.getAWS_CREDENTIAL_FILE());
			ephemeralNetworkCreator.copyToAnsibleSecrets(GCP_CREDENTIAL, Constants.getGCP_CREDENTIAL_FILE());

		}


		@Test
		public void tests() {
			String name = Generic.extractTestName(this.name.getMethodName());
			logger.info("Test name is " + name);

			String CORE_TAG = Optional.ofNullable(System.getenv(EphemeralNetworkCreator.ENV_CORE_TAG)).orElse(":HEAD-043ccbdc");
			String TESTNET_NAME = System.getenv(EphemeralNetworkCreator.ENV_TESTNET_NAME);
			String LOG_LEVEL = Optional.ofNullable(System.getenv(EphemeralNetworkCreator.ENV_LOG_LEVEL)).orElse("debug");
			ephemeralNetworkCreator.setTotalNumberOfNodes(networkSize);
			ephemeralNetworkCreator.pullImage();
			ephemeralNetworkCreator.plan();
			ephemeralNetworkCreator.setup();
			ephemeralNetworkCreator.nodesToBringdown(TESTNET_NAME);
			ephemeralNetworkCreator.deploy(Collections.emptyList(),
				Stream
					.of(" -i aws-inventory  ",
						"--limit " + TESTNET_NAME + " ",
						"-e RADIXDLT_UNIVERSE ",
						"-e core_tag=" + CORE_TAG + " ",
						"-e core_log_level="+ LOG_LEVEL + " ",
						"-e boot_nodes=\"{{ groups['"+ TESTNET_NAME +"'] | join(',') }}\" ",
						"-e quorum_size="+ networkSize +" -e consensus_start_on_boot=true").collect(toList()));

			network = ephemeralNetworkCreator.getNetwork(networkSize);

			RemoteBFTTest test = AssertionChecks.slowNodeTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(30, TimeUnit.SECONDS);

			crashedNodesURLs = new ArrayList<>(network.getNodeIds())
				.stream()
				.limit(nodesCrashed)
				.collect(Collectors.toList());
			String nodesToBringdown = Generic.listToDelimitedString(crashedNodesURLs
																	.stream()
																	.map(Generic::getDomainName)
																	.collect(Collectors.toList()),",");

			ephemeralNetworkCreator.nodesToBringdown(nodesToBringdown);


			RemoteBFTTest testOutOfSynchronyBounds = outOfSynchronyTestBuilder((ArrayList<String>) crashedNodesURLs)
				.network(RemoteBFTNetworkBridge.of(network))
				.build();
			testOutOfSynchronyBounds.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
		}

		@After
		public void removeCluster() {
			String TESTNET_NAME = System.getenv(EphemeralNetworkCreator.ENV_TESTNET_NAME);
			List<String> runningNodes = new ArrayList<>(network.getNodeIds())
				.stream()
				.filter(this::isNodeRunning)
				.map(Generic::getDomainName)
				.collect(Collectors.toList());
			ephemeralNetworkCreator.captureLogs(
				Generic.listToDelimitedString(runningNodes,","),
				Generic.extractTestName(this.name.getMethodName()));
			ephemeralNetworkCreator.teardown();
			ephemeralNetworkCreator.volumeCleanUp();
		}

		private boolean isNodeRunning(String nodeUrl) {
			if (crashedNodesURLs == null) {
				return true;
			}
			return !crashedNodesURLs.contains(nodeUrl);
		}
	}
}
