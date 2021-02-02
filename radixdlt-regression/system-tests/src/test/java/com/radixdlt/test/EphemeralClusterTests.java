/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    @Parameters(name = "{index}: given_{0}_correct_bfts_in_cluster_network__when_{1}_node_is_down__"
		+ "then_all_other_instances_should_get_same_commits_and_progress_should_be_made")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {11, 2},
                {11, 3},
                {5, 2}
        });
    }

    @Before
    public void setupCluster() {
        String sshIdentity = Optional.ofNullable(System.getenv(EphemeralNetworkCreator.ENV_SSH_IDENTITY))
                .orElse(System.getenv("HOME") + "/.ssh/id_rsa");
        String sshIdentityPub = Optional.ofNullable(System.getenv(EphemeralNetworkCreator.ENV_SSH_IDENTITY_PUB))
                .orElse(System.getenv("HOME") + "/.ssh/id_rsa.pub");
        String awsCredential = System.getenv(EphemeralNetworkCreator.ENV_AWS_CREDENTIAL);
        String gcpCredential = System.getenv(EphemeralNetworkCreator.ENV_GCP_CREDENTIAL);
        String credentialVolName = Optional.ofNullable(System.getenv(Constants.getCREDENTIAL_VOL_NAME()))
                .orElse("key-volume");


        ephemeralNetworkCreator = EphemeralNetworkCreator.builder()
                .withTerraformImage("eu.gcr.io/lunar-arc-236318/node-terraform:latest")
                .withAnsibleImage("eu.gcr.io/lunar-arc-236318/node-ansible:python3")
                .withKeyVolume(credentialVolName)
                .withTerraformOptions(Collections.emptyList()).build();

        ephemeralNetworkCreator.copyToTFSecrets(sshIdentity);
        ephemeralNetworkCreator.copyToTFSecrets(sshIdentityPub, Constants.getTESTNET_SSH_KEY_FILE_NAME());
        ephemeralNetworkCreator.copyToTFSecrets(awsCredential, Constants.getAWS_CREDENTIAL_FILE());

        ephemeralNetworkCreator.copyToAnsibleSecrets(awsCredential, Constants.getAWS_CREDENTIAL_FILE());
        ephemeralNetworkCreator.copyToAnsibleSecrets(gcpCredential, Constants.getGCP_CREDENTIAL_FILE());
    }

    @Test
    public void tests() {
        String name = Generic.extractTestName(testNameRule.getMethodName());
        logger.info("Test name is " + name);

        String coreTag = Optional.ofNullable(System.getenv(EphemeralNetworkCreator.ENV_CORE_TAG)).orElse(":HEAD-043ccbdc");
        String testnetName = System.getenv(EphemeralNetworkCreator.ENV_TESTNET_NAME);
        String logLevel = Optional.ofNullable(System.getenv(EphemeralNetworkCreator.ENV_LOG_LEVEL)).orElse("debug");
        ephemeralNetworkCreator.setTotalNumberOfNodes(networkSize);
        ephemeralNetworkCreator.pullImage();
        ephemeralNetworkCreator.plan();
        ephemeralNetworkCreator.setup();
        ephemeralNetworkCreator.nodesToBringdown(testnetName);
        ephemeralNetworkCreator.deploy(Collections.emptyList(),
                Stream
                        .of(" -i aws-inventory  ",
                                "--limit " + testnetName + " ",
                                "-e RADIXDLT_UNIVERSE ",
                                "-e core_tag=" + coreTag + " ",
                                "-e core_log_level=" + logLevel + " ",
                                "-e boot_nodes=\"{{ groups['" + testnetName + "'] | join(',') }}\" ",
                                "-e quorum_size=" + networkSize + " -e consensus_start_on_boot=true").collect(toList()));

        network = ephemeralNetworkCreator.getNetwork(networkSize);

        RemoteBFTTest test = AssertionChecks.slowNodeTestBuilder()
                .network(RemoteBFTNetworkBridge.of(network))
                .waitUntilResponsive()
                .startConsensusOnRun()
                .build();
        test.runBlocking(30, TimeUnit.SECONDS);

        crashedNodesURLs = network.getNodeIds()
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
        List<String> runningNodes = network.getNodeIds()
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
