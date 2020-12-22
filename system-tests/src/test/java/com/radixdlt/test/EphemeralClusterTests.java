package com.radixdlt.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import utils.CmdHelper;
import utils.Constants;
import utils.EphemeralNetworkCreator;
import utils.Generic;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.radixdlt.test.AssertionChecks.outOfSynchronyTestBuilder;
import static java.util.stream.Collectors.toList;

@Category(EphemeralCluster.class)
@RunWith(Parameterized.class)
public class EphemeralClusterTests {

    private static final Logger logger = LogManager.getLogger();

    private EphemeralNetworkCreator ephemeralNetworkCreator;
    private StaticClusterNetwork network;

    @Rule
    public TestName testNameRule = new TestName();

    private int networkSize, nodesCrashed;
    private List<String> crashedNodesURLs;

    public EphemeralClusterTests(int networkSize, int nodesCrashed) {
        this.networkSize = networkSize;
        this.nodesCrashed = nodesCrashed;
    }

    @Parameters(name = "{index}: given_{0}_correct_bfts_in_cluster_network__when_{1}_node_is_down__then_all_other_instances_should_get_same_commits_and_progress_should_be_made")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {11, 2},
                {11, 3},
                {5, 2}
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
        ephemeralNetworkCreator.copyToTFSecrets(SSH_IDENTITY_PUB, Constants.getTESTNET_SSH_KEY_FILE_NAME());
        ephemeralNetworkCreator.copyToTFSecrets(AWS_CREDENTIAL, Constants.getAWS_CREDENTIAL_FILE());

        ephemeralNetworkCreator.copyToAnsibleSecrets(AWS_CREDENTIAL, Constants.getAWS_CREDENTIAL_FILE());
        ephemeralNetworkCreator.copyToAnsibleSecrets(GCP_CREDENTIAL, Constants.getGCP_CREDENTIAL_FILE());
    }

    @Test
    public void tests() {
        String name = Generic.extractTestName(testNameRule.getMethodName());
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
                                "-e core_log_level=" + LOG_LEVEL + " ",
                                "-e boot_nodes=\"{{ groups['" + TESTNET_NAME + "'] | join(',') }}\" ",
                                "-e quorum_size=" + networkSize + " -e consensus_start_on_boot=true").collect(toList()));

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
                .collect(Collectors.toList()), ",");

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
                Generic.listToDelimitedString(runningNodes, ","),
                Generic.extractTestName(this.testNameRule.getMethodName()));
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
