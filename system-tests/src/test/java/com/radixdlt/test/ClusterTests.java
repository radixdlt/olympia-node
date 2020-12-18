package com.radixdlt.test;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import utils.CmdHelper;
import utils.SlowNodeSetup;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * BFT tests against network where all nodes are under synchrony bounds and one or more nodes slow.
 */
@Category(Cluster.class)
public class ClusterTests {

    private static final Logger logger = LogManager.getLogger();

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

    @After
    public void removeSlowNodesettings() {
        slowNodeSetup.tearDown();
    }

}
