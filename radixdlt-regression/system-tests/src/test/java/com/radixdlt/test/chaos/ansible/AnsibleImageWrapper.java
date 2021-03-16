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

package com.radixdlt.test.chaos.ansible;

import com.radixdlt.test.RemoteBFTNetwork;
import com.radixdlt.test.StaticClusterNetwork;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Sets;
import org.junit.platform.commons.util.StringUtils;
import utils.CmdHelper;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * We have our own image with ansible and several playbook. We use this image via docker.
 * This class has the docker commands needed to utilize these playbooks.
 */
public class AnsibleImageWrapper {

    private static final Logger logger = LogManager.getLogger();

    public static final String DEFAULT_ANSIBLE_IMAGE = "eu.gcr.io/lunar-arc-236318/node-ansible";
    private static final String KEY_VOLUME_NAME_USED_FOR_COPYING = "key-volume";

    private final String image;
    private final String clusterName;
    private Set<String> nodeAddresses;

    public static AnsibleImageWrapper createWithDefaultImage() {
        String sshKeyLocation = ChaosExperimentUtils.getSshIdentityLocation();
        String clusterName = Optional.ofNullable(System.getenv("TESTNET_NAME")).orElseThrow();
        // the ansible image needs an SSH key because it runs tasks over SSH
        AnsibleImageWrapper wrapper = new AnsibleImageWrapper(DEFAULT_ANSIBLE_IMAGE, clusterName);
        wrapper.copyfileToNamedVolume(sshKeyLocation);
        wrapper.pullImage();
        return wrapper;
    }

    private AnsibleImageWrapper(String image, String clusterName) {
        this.image = image;
        this.clusterName = clusterName;
    }

    /**
     * The key needs to be copied to a volume, which is created with the help of a temp dummy container
     */
    private void copyfileToNamedVolume(String localFileLocation) {
        CmdHelper.runCommand(String.format("docker container create --name dummy -v %s:%s curlimages/curl:7.70.0",
                KEY_VOLUME_NAME_USED_FOR_COPYING, "/ansible/ssh"));
        CmdHelper.runCommand(String.format("docker cp %s dummy:%s",
                localFileLocation, "/ansible/ssh/testnet"));
        CmdHelper.runCommand("docker rm -f dummy");
    }

    private void pullImage() {
        CmdHelper.runCommand("docker pull " + image);
    }

    public String runPlaybook(String playbook, String options, String tag) {
        String optionsAsEnvProperty = StringUtils.isBlank(options) ? "" : "-e \"optionsArgs='" + options + "'\" ";
        List<String> commandParts = Lists.newArrayList(
                "docker", "run", "--rm", "-v", "key-volume:/ansible/ssh", "--name", "node-ansible",
                image, playbook, optionsAsEnvProperty, "--limit", clusterName, "-t", tag
        );
        String[] commandArrayWithoutEmptyStrings =
                commandParts.stream().filter(StringUtils::isNotBlank).toArray(String[]::new);
        logger.info("Running docker command: {}", commandParts);
        return CmdHelper.runCommand(commandArrayWithoutEmptyStrings, null, true).toString();
    }

    public String runPlaybook(String playbook, String tag) {
        return runPlaybook(playbook, null, tag);
    }

    /**
     * Uses the output of the check task to get a list of node IPs
     */
    public Set<String> getNodeAddressList() {
        if (nodeAddresses == null) {
            nodeAddresses = Sets.newHashSet();
            String playbookOutput = runPlaybook("check.yml", "check");
            Matcher matcher = Pattern.compile("(?<=\\[).+?(?=\\])").matcher(playbookOutput);
            while (matcher.find()) {
                String raw = matcher.group(0);
                if (raw.split("[.]").length == 4) {
                    nodeAddresses.add(raw);
                }
            }
            logger.info("Found {} nodes", nodeAddresses.size());
        }
        return nodeAddresses;
    }

    /**
     * returns one of the node addresses
     */
    public String getRandomNodeHost() {
        List<String> addressList = Lists.newArrayList(getNodeAddressList());
        return addressList.get(new Random().nextInt(addressList.size()));
    }

    /**
     * This is needed to bridge the older code (using {@link RemoteBFTNetwork} and the tests here.
     * Hardcoded to HTTPS, but can be externalized to a property.
     */
    public RemoteBFTNetwork toNetwork() {
        return StaticClusterNetwork.from(getNodeAddressList().stream().map(address -> "https://" + address)
                .collect(Collectors.toSet()));
    }

    public void tearDown() {
        CmdHelper.runCommand("docker volume rm -f " + KEY_VOLUME_NAME_USED_FOR_COPYING);
    }

}
