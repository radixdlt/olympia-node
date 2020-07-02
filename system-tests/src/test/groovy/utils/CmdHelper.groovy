/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package utils

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import static utils.Generic.listToDelimitedString

class CmdHelper {
    private static final Logger logger = LogManager.getLogger()

    static List<String[]> runCommand(cmd, String[] env = null, failOnError = false, logOutput = true) {

        Thread.sleep(1000)
        def sout = new StringBuffer()
        def serr = new StringBuffer()
        def process
        logger.info("------Executing command ${cmd}-----")
        if (env) {
            logger.info("------Environment variables ${env}-----")
            process = cmd.execute(env as String[], null)
        } else {
            process = cmd.execute()
        }

        process.consumeProcessOutput(sout, serr)

        //  This sleep added to allow some shell commands to run reliably.
        //  Ex : Below command was not consistently giving output from docker container that is running with host network. Adding 1000 ms gave better result
        //      "grep -l 351 /sys/class/net/veth*/ifindex"
        Thread.sleep(1000)
        process.waitFor()

        List output
        if (sout) {
            output = sout.toString().split(System.lineSeparator()).collect({ it })
            if (logOutput) {
                logger.info("-----------Output---------")
                sout.each { logger.info(it) }
            }
        }

        List error
        if (serr) {
            logger.error("-----------Error---------")

            serr.each { logger.error(it) }
            error = serr.toString().split(System.lineSeparator()).collect({ it })
            if (failOnError) {
                throw new Exception(error.toString())
            }
        }
        return [output, error]
    }


    static List node(options) {
        String[] env = ["JAVA_OPTS=-server -Xms2g -Xmx2g -Djava.security.egd=file:/dev/urandom -Dcom.sun.management.jmxremote.port=${options.rmiPort} -Dcom.sun.management.jmxremote.rmi.port=${options.rmiPort} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost -agentlib:jdwp=transport=dt_socket,address=${options.socketAddressPort},suspend=n,server=y",
                        "RADIXDLT_NETWORK_SEEDS_REMOTE=${listToDelimitedString(options.remoteSeeds)}",
                        "RADIXDLT_CONSENSUS_FIXED_NODE_COUNT=${options.quorumSize}",
                        "RADIXDLT_HOST_IP_ADDRESS=${options.nodeName}",
                        "RADIXDLT_CONSENSUS_START_ON_BOOT=${options.startConsensusOnBoot}",

        ]
        String dockerContainer = "docker run -d " +
                "-e RADIXDLT_NETWORK_SEEDS_REMOTE " +
                "--name ${options.nodeName}  " +
                "-e RADIXDLT_CONSENSUS_FIXED_NODE_COUNT " +
                "-e RADIXDLT_HOST_IP_ADDRESS " +
                "-e RADIXDLT_CONSENSUS_START_ON_BOOT " +
                "-e JAVA_OPTS " +
                "-l com.radixdlt.roles='core' " +
                "-p ${options.hostPort}:8080 " +
                "--network ${options.network} " +
                "radixdlt/radixdlt-core:develop"
        return [env as String[], dockerContainer]
    }


    static Map getDockerOptions(int nodeCount, boolean startConsensusOnBoot) {

        List<String> nodeNames = (1..nodeCount).collect({ return "core${it}".toString() })
        return nodeNames.withIndex().collectEntries { node, index ->
            Map options = [:]
            options.nodeName = node
            options.quorumSize = nodeCount
            options.remoteSeeds = nodeNames.findAll({ it != node })
            options.hostPort = 1080 + index
            options.rmiPort = 9010 + index
            options.socketAddressPort = 50505 + index
            options.startConsensusOnBoot = startConsensusOnBoot
            return [(node): options]
        }

    }

    static void removeAllDockerContainers(String name = "core") {
        def psOutput, psError
        (psOutput, psError) = runCommand("docker ps -a")
        psOutput
                .findAll({ !it.contains("IMAGE") })
                .findAll({ it.contains(name) })
                .collect({ return it.split(" ")[0] })
                .each { runCommand("docker rm -f ${it}") }
    }

    static void checkNGenerateKey() {
        def file = new File(Generic.keyStorePath())
        if (!file.exists()) {
            List options = ["generate-key", "--password=test123"]
            def key, error
            (key, error) = runCommand("java -jar ${Generic.pathToCLIJar()} ${listToDelimitedString(options, ' ')}", null, true, false)
            file.withWriter('UTF-8') { writer ->
                key.each {
                    writer.write(it)
                }
            }
        }
    }

    static String radixCliCommand(List cmdOptions) {
        return "java -jar ${Generic.pathToCLIJar()} ${listToDelimitedString(cmdOptions, ' ')}"
    }

    /**
     * For a given container name, method returns the virtual ethernet of the docker container. This method executes a
     * command on a docker container with @param name to find system wide unique index of the interface  it is linked to. Typically a container has interface
     * eth0 and iflink has an index in decimal number that is mapped to virtual ethernet interface on host
     * @param  name  name of the container who's virtual ethernet interface needs to be retrieved
     * @return string value of Virtual ethernet name that docker container is linked to on host
     */
    static String getVethByContainerName(name) {
        def iflink, netId, veth, error, out

        // Below command executes on requested docker container to find the unique index of the interface it is linked to host
        def command = "docker exec ${name} bash -c".tokenize() << "cat /sys/class/net/eth*/iflink"
        (iflink, error) = runCommand(command)
        Closure getVeth = {
            // Below command executed to find a veth* files that match index number.
            // More details on /sys/class/net can be found on this link https://www.kernel.org/doc/Documentation/ABI/testing/sysfs-class-net
            def string = "bash -c".tokenize() << ("grep -l ${Integer.parseInt(iflink[0])} /sys/class/net/veth*/ifindex" as String)
            (veth, error) = runCommand(string)
            return veth
        }

        // Commands from a docker container using host network sometime do not run in a reliable way for locations /sys/class/net esp when running on docker container
        //  To overcome this Closure getVeth is called maximum three times to see if return value is empty list not empty. Retrying three times should give the value otherwise there may other issues
        veth = getVeth()
        def count = 0
        while (veth.size() == 0) {
            getVeth()
            count++
            if (count > 3) {
                break;
            }
        }
        println(veth[0].tokenize("/").find({ it.contains("veth") }))
        return veth[0].tokenize("/").find({ it.contains("veth") })
    }

    static String setupIPTables() {
        [
                "iptables -A POSTROUTING -t mangle -j CLASSIFY --set-class 10:10 -p tcp",
                "iptables -A POSTROUTING -t mangle -j CLASSIFY --set-class 10:10 -p udp",
                "iptables -A POSTROUTING -t mangle -j CLASSIFY --set-class 10:10 -p icmp",
                "ip6tables -A POSTROUTING -t mangle -j CLASSIFY --set-class 10:10 -p tcp",
                "ip6tables -A POSTROUTING -t mangle -j CLASSIFY --set-class 10:10 -p udp",
                "ip6tables -A POSTROUTING -t mangle -j CLASSIFY --set-class 10:10 -p icmp"
        ].each { runCommand(it) }
    }

    static String flushIPTableMangle() {
        runCommand("iptables -t mangle -F")
    }

    static String setupQueueQuality(veth, optionsArgs = "delay 100ms loss 20%") {
        runCommand("tc qdisc add dev ${veth} handle 10: root netem ${optionsArgs}")
    }

    static void createNamedVolume(String sshKeylocation) {
        runCommand("docker container create --name dummy -v key-volume:/ansible/ssh curlimages/curl:7.70.0", null);
        runCommand(String.format("docker cp %s dummy:/ansible/ssh/testnet",sshKeylocation));
        runCommand("docker rm -f dummy");
    }
    static void pullAnsibleImage(image="eu.gcr.io/lunar-arc-236318/node-ansible:latest"){
        runCommand("docker pull ${image}");
    }
    static void setupSlowNodeOnCluster(){
        runCommand("docker run --rm  -v key-volume:/ansible/ssh --name node-ansible eu.gcr.io/lunar-arc-236318/node-ansible:latest  "
                + "slow-down-node.yml -vv  --limit testnet_2[0] -t setup");
    }

    static def void tearDownSlowNodeSettings() {
        runCommand("docker run --rm  -v key-volume:/ansible/ssh --name node-ansible eu.gcr.io/lunar-arc-236318/node-ansible:latest  "
                + "slow-down-node.yml -vv  --limit testnet_2[0] -t teardown");
        runCommand("docker volume rm -f key-volume");

    }
}
