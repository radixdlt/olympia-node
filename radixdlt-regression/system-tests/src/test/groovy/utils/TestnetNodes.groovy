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

package utils

import com.google.common.collect.ImmutableSet
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class TestnetNodes {

    private static final Logger logger = LogManager.getLogger(TestnetNodes.class);
    private static final String scheme = "https"
    private String additionalCommandOptions
    private String additionalDockerOptions

    public TestnetNodes() {
    }


    private List<String> nodes
    String keyVolume = "key-volume"
    private String sshDestinationLocDir = "/ansible/ssh"
    private String sshDestinationFileName = "testnet"
    String ansibleImage = "eu.gcr.io/lunar-arc-236318/node-ansible"


    TestnetNodes usingCmdOptions(String cmdOptions) {
        this.additionalCommandOptions = cmdOptions
        return this
    }

    TestnetNodes usingDockerRunOptions(String additionalDockerOptions) {
        this.additionalDockerOptions = additionalDockerOptions
        return this
    }

    String getNodesURls() {
        fetchNodes()
        return nodes.inject("", {
            str, node ->
                if (this.nodes.first() == node)
                    return "${str}https://${node}"
                return "${str},https://${node}"
        })
    }

    ImmutableSet<String> nodeURLList() {
        fetchNodes()

        return ImmutableSet.copyOf(this.nodes.collect {
            "${scheme}://${it}" as String
        });
    }

    private fetchNodes() {
        if (nodes == null) {
            String clusterName = Optional.ofNullable(System.getenv("TESTNET_NAME")).orElse("testnet_2")
            logger.info("Node information not avaliable. Fetching using ansible")

            String sshKeylocation = Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa")
            CmdHelper.runCommand("docker container create --name dummy -v ${keyVolume}:${sshDestinationLocDir} curlimages/curl:7.70.0")
            CmdHelper.runCommand("docker cp ${sshKeylocation} dummy:${sshDestinationLocDir}/${sshDestinationFileName}")
            CmdHelper.runCommand("docker rm -f dummy")

            def output, error
            def default_dockerOptions = "-v ${keyVolume}:/ansible/ssh"
            def runCommand = "bash -c".tokenize() << (
                    "docker run --rm  " +
                            "${additionalDockerOptions ?: default_dockerOptions} " +
                            "--name node-ansible ${ansibleImage}  " +
                            "check.yml " +
                            "${additionalCommandOptions ?: ''} " +
                            "--limit ${clusterName} --list-hosts" as String)
            (output, error) = CmdHelper.runCommand(runCommand)
            nodes = output.findAll({
                !(it.contains("play") || it.contains("pattern") || it.contains("hosts") || it == "")
            }).collect({ it.trim() })

        } else {
            logger.info("Nodes information already present ${nodes}")
        }
    }
}
