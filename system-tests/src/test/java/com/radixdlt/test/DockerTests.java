package com.radixdlt.test;

import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import utils.CmdHelper;
import utils.Generic;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.radixdlt.test.AssertionChecks.outOfSynchronyTestBuilder;
import static com.radixdlt.test.AssertionChecks.slowNodeTestBuilder;

@Category(Docker.class)
public class DockerTests {

    private static final Logger logger = LogManager.getLogger();

    @Rule
    public TestName testNameRule = new TestName();

    private String testName;

    @Before
    public void setup() {
        testName = Generic.extractTestName(this.testNameRule.getMethodName());
        logger.info("Test name is {}", testName);
    }

    @Test
    public void given_5_correct_bfts_in_latent_docker_network__when_one_instance_is_down__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
        try (DockerNetwork network = DockerNetwork.builder().numNodes(5).testName(testName).startConsensusOnBoot().build()) {
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

    @Parameters({"4", "5"})
    @TestCaseName("given_{0}_correct_bfts_in_latent_docker_network__when_all_nodes_are_out_synchrony__then_a_liveness_check_should_fail")
    @Test
    public void given_X_correct_bfts_in_latent_docker_network__when_all_nodes_are_out_synchrony__then_a_liveness_check_should_fail(int numberOfNodes) {
        String name = Generic.extractTestName(this.testNameRule.getMethodName());
        logger.info("Test name is " + name);
        try (DockerNetwork network = DockerNetwork.builder().numNodes(numberOfNodes).testName(name).startConsensusOnBoot().build()) {
            network.startBlocking();

            Conditions.waitUntilNetworkHasLiveness(network);

            network.getNodeIds().forEach(nodeId -> CmdHelper.blockPort(nodeId, 30000)); // unfortunately a magic number, should be read from a config instead

            RemoteBFTTest testOutOfSynchronyBounds = RemoteBFTTest.builder().assertLiveness(20)
                    .network(RemoteBFTNetworkBridge.of(network)).build();

            AssertionError error = Assert.assertThrows(AssertionError.class, () ->
                    testOutOfSynchronyBounds.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS)
            );

            Generic.assertAssertionErrorIsLivenessError(error);
        }
    }

    @Parameters({"3", "5"})
    @TestCaseName("given_{0}_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made")
    @Test
    public void given_X_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made(int numberOfNodes) {
        try (DockerNetwork network = DockerNetwork.builder().numNodes(numberOfNodes).testName(testName).build()) {
            network.startBlocking();
            String veth = CmdHelper.getVethByContainerName(network.getNodeIds().stream().findFirst().get());
            CmdHelper.setupQueueQuality(veth, "delay 100ms loss 20%");

            RemoteBFTTest test = AssertionChecks
                    .slowNodeTestBuilder()
                    .network(RemoteBFTNetworkBridge.of(network))
                    .waitUntilResponsive()
                    .startConsensusOnRun().build();
            test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
        }
    }

    @Parameters({"3", "5"})
    @TestCaseName("given_{0}_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made")
    @Test
    public void given_X_correct_bfts_in_latent_docker_network__then_all_instances_should_get_same_commits_and_progress_should_be_made(int numberOfNodes) {
        try (DockerNetwork network = DockerNetwork.builder().numNodes(numberOfNodes).testName(testName).build()) {
            network.startBlocking();
            RemoteBFTTest test = AssertionChecks.latentTestBuilder()
                    .network(RemoteBFTNetworkBridge.of(network))
                    .waitUntilResponsive()
                    .startConsensusOnRun()
                    .build();
            test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
        }
    }


}
