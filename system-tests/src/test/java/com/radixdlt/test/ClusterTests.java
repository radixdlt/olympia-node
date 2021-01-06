package com.radixdlt.test;


import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import utils.CmdHelper;
import utils.Generic;
import utils.SlowNodeSetup;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.radixdlt.test.AssertionChecks.outOfSynchronyTestBuilder;

/**
 * BFT tests against network where all nodes are under synchrony bounds and one or more nodes slow.
 */
@Category(Cluster.class)
public class ClusterTests {

    private static final Logger logger = LogManager.getLogger();

    @Rule
    public TestName testNameRule = new TestName();

    private SlowNodeSetup slowNodeSetup;
    private StaticClusterNetwork network;

    @Before
    public void setupSlowNode() {
        logger.info("Test name is {}", Generic.extractTestName(testNameRule.getMethodName()));

        network = StaticClusterNetwork.clusterInfo(10);
        String sshKeylocation = Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa");

        //Creating named volume and copying over the file to volume works with or without docker in docker setup
        slowNodeSetup = SlowNodeSetup.builder()
                .withImage("eu.gcr.io/lunar-arc-236318/node-ansible")
                .nodesToSlowDown(1)
                .usingCluster(network.getClusterName())
                .runOptions("--rm -v key-volume:/ansible/ssh --name node-ansible")
                .build();
        slowNodeSetup.copyfileToNamedVolume(sshKeylocation, "key-volume");
        slowNodeSetup.pullImage();
        slowNodeSetup.setup();
    }

    @Test
    public void given_10_correct_bfts_in_latent_cluster_network__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
        final StaticClusterNetwork network = StaticClusterNetwork.clusterInfo(10);
        RemoteBFTTest test = AssertionChecks.latentTestBuilder()
                .network(RemoteBFTNetworkBridge.of(network))
                .waitUntilResponsive()
                .startConsensusOnRun() // in case we're the first to access the cluster
                .build();
        test.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS);
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

    @Test
    public void given_10_correct_bfts_in_latent_cluster_network__when_all_nodes_are_out_synchrony__then_a_liveness_check_should_fail() {
        try {
            network = StaticClusterNetwork.clusterInfo(10);
            Conditions.waitUntilNetworkHasLiveness(network);

            // The SlowNodeSetup object here is used only to run ansible playbook tasks via the togglePortViaAnsible() method
            slowNodeSetup.togglePortViaAnsible(30000, true);

            RemoteBFTTest testOutOfSynchronyBounds = outOfSynchronyTestBuilder(Lists.newArrayList())
                    .network(RemoteBFTNetworkBridge.of(network))
                    .build();

            AssertionError error = Assert.assertThrows(AssertionError.class, () ->
                    testOutOfSynchronyBounds.runBlocking(CmdHelper.getTestDurationInSeconds(), TimeUnit.SECONDS)
            );

            Generic.assertAssertionErrorIsLivenessError(error);
        } finally {
            slowNodeSetup.togglePortViaAnsible(30000, false);
        }
    }

    @After
    public void teardown() {
        Conditions.waitUntilNetworkHasLiveness(network);
    }

    @After
    public void removeSlowNodesettings() {
        slowNodeSetup.tearDown();
    }

}
