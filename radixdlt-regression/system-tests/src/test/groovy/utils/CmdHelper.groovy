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

import me.alexpanov.net.FreePortFinder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import java.nio.file.Files
import java.nio.file.Paths

class CmdHelper {
    private static final Logger logger = LogManager.getLogger()

    static List<String[]> runCommand(cmd, String[] env = null, failOnError = false, logOutput = true, String workdir = null) {

        Thread.sleep(1000)
        def sout = new StringBuffer()
        def serr = new StringBuffer()
        def process
        logger.debug("------Executing command ${cmd}-----")
        env ? logger.debug("------Environment variables ${env}-----") : ""
        workdir ? logger.debug("------Working dir ${workdir}-----") : ""
        process = cmd.execute(
                env ?: null,
                workdir ? new File(workdir) : null
        )

        process.consumeProcessOutput(sout, serr)

        //  This sleep added to allow some shell commands to run reliably.
        //  Ex : Below command was not consistently giving output from docker container that is running with host network. Adding 1000 ms gave better result
        //      "grep -l 351 /sys/class/net/veth*/ifindex"
        Thread.sleep(1000)
        process.waitFor()

        List output
        if (sout) {
            output = sout.toString().split("\n").collect({ it })
            if (logOutput) {
                logger.debug("-----------Output---------")
                sout.each { logger.debug(it) }
            }
        }

        List error
        if (serr) {
            logger.error("-----------Error---------")
            serr.each { logger.error(it) }
            error = serr.toString().split("\n").collect({ it })
            if (failOnError) {
                throw new Exception(error.toString())
            }
        }
        return [output, error]
    }


    static List node(options, universe, validatorKey) {
        String[] env = ["JAVA_OPTS=-server -Xms2g -Xmx2g -Djava.security.egd=file:/dev/urandom -Dcom.sun.management.jmxremote.port=${options.rmiPort} -Dcom.sun.management.jmxremote.rmi.port=${options.rmiPort} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost -agentlib:jdwp=transport=dt_socket,address=${options.socketAddressPort},suspend=n,server=y",
                        "RADIXDLT_NETWORK_SEEDS_REMOTE=${listToDelimitedString(options.remoteSeeds)}",
                        "RADIXDLT_HOST_IP_ADDRESS=${options.nodeName}",
                        "RADIXDLT_CONSENSUS_START_ON_BOOT=${options.startConsensusOnBoot}",
                        "RADIXDLT_UNIVERSE=${universe}",
                        "RADIXDLT_NODE_KEY=${validatorKey}",
                        "RADIXDLT_LOG_LEVEL=debug"
        ]

        String hostPortMapping = "-p ${options.hostPort}:8080 "
        String dockerContainer = "docker run -d " +
                "-e RADIXDLT_NETWORK_SEEDS_REMOTE " +
                "--name ${options.nodeName}  " +
                "-e RADIXDLT_HOST_IP_ADDRESS " +
                "-e RADIXDLT_CONSENSUS_START_ON_BOOT " +
                "-e RADIXDLT_UNIVERSE " +
                "-e JAVA_OPTS " +
                "-e RADIXDLT_NODE_KEY " +
                "-l com.radixdlt.roles='core' " +
                "${testRunningOnDocker() ? '' : hostPortMapping} " +
                "--cap-add=NET_ADMIN " +
                "--network ${options.network} " +
                "radixdlt/radixdlt-core:develop"
        return [env as String[], dockerContainer]
    }

    /**
     * Blocks tcp communication over a specific port, via iptables
     */
    static String blockPort(String containerName, int port) {
        return managePort(containerName, port, true);
    }

    /**
     * removes the iptables rule which block the tcp port
     */
    static String unblockPort(String containerName, int port) {
        return managePort(containerName, port, false);
    }

    static private String managePort(String containerName, int port, boolean enableRule) {
        def parameter = enableRule ? "-A" : "-D"
        def iptablesCommand = "iptables ${parameter} OUTPUT -p tcp --dport ${port} -j DROP"
        def (output, error) = runCommand("docker exec ${containerName} bash -c".tokenize() << iptablesCommand, null, true, true)
        return output;
    }

    static Map getDockerOptions(int nodeCount, boolean startConsensusOnBoot) {
        List<String> nodeNames = (1..nodeCount).collect({ return "${getContainerNamePrefix()}${it}".toString() })
        return nodeNames.withIndex().collectEntries { node, index ->
            Map options = [:]
            options.nodeName = node
            options.quorumSize = nodeCount
            options.remoteSeeds = nodeNames.findAll({ it != node })
            options.hostPort = FreePortFinder.findFreeLocalPort()
            options.rmiPort = FreePortFinder.findFreeLocalPort();
            options.socketAddressPort = FreePortFinder.findFreeLocalPort();
            options.startConsensusOnBoot = startConsensusOnBoot
            options.nodeIndex = index
            return [(node): options]
        }

    }

    static String getContainerNamePrefix() {
        //CONTAINER_NAME is system env that can used in jenkins and could reference job name + number
        return System.getenv("CONTAINER_NAME") ?: "core"
    }

    static int getTestDurationInSeconds() {
        try {
            logger.info("TEST_DURATION system variable is '${System.getenv("TEST_DURATION")}'")
            return Integer.parseInt(System.getenv("TEST_DURATION"))
        } catch (Exception ex) {
            logger.info("Exception occurred in parsing the test duration setting it default value. Is TEST_DURATION environment variable set properly?" +
                    "\n Default value 60 seconds for test duration is being used instead ")
            return 60

        }
    }

    static void removeAllDockerContainers() {
        def name = getContainerNamePrefix()
        def psOutput, psError
        (psOutput, psError) = runCommand("docker ps -a")
        psOutput
                .findAll({ !it.contains("IMAGE") })
                .findAll({ it.contains(name) })
                .collect({
                    println "container to be removed ${it}"
                    return it.split(" ")[0]
                })
                .each {
                    runCommand("docker rm -f ${it}")
                }
    }

    /**
     * TODO rethink this
     */
    static void checkNGenerateKey() {
//        def file = new File(Generic.keyStorePath()) // TODO fix this
//        def file = new File()
//        if (!file.exists()) {
//            List options = ["generate-key", "--password=test123"]
//            def key, error
//            (key, error) = runCommand("java -jar ${Generic.pathToCLIJar()} ${listToDelimitedString(options, ' ')}", null, true, false)
//            file.withWriter('UTF-8') { writer ->
//                key.each {
//                    writer.write(it)
//                }
//            }
//        }
    }

    static String runContainer(String dockerCommand, String[] dockerEnv) {
        def results = isRunningOnWindows() ?
                runCommand(dockerCommand.tokenize(), dockerEnv, true) :
                runCommand("/bin/bash -c".tokenize() << dockerCommand, dockerEnv, true)
        return results[0][0]
    }

    /**
     * gets the veth for the given container name and then runs tc on it
     */
    static String runTcUsingVeth(containerName, optionsArgs = "delay 100ms loss 20%") {
        String veth = getVethByContainerName(containerName);
        setupQueueQuality(veth, optionsArgs)
    }

    /**
     * For a given container name, method returns the virtual ethernet of the docker container. This method executes a
     * command on a docker container with @param name to find system wide unique index of the interface  it is linked to. Typically a container has interface
     * eth0 and iflink has an index in decimal number that is mapped to virtual ethernet interface on host
     * @param name name of the container who's virtual ethernet interface needs to be retrieved
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
            def string = "/bin/bash -c".tokenize() << ("grep -l ${Integer.parseInt(iflink[0])} /sys/class/net/veth*/ifindex" as String)
            (veth, error) = runCommand(string)
            if (error) {
                throw new IllegalStateException("Docker container is not running ")
            }
            return veth
        }

        // Commands from a docker container using host network sometime do not run in a reliable way for locations /sys/class/net esp when running on docker container
        //  To overcome this Closure getVeth is called maximum three times to see if return value is empty. Retrying three times should give the value otherwise there may be some other issues
        veth = getVeth()
        def count = 0
        while (veth == null || veth.size() == 0) {
            veth = getVeth()
            count++
            if (count > 3) {
                break;
            }
        }
        if (veth == null || veth.size() == 0) {
            throw new IllegalStateException("Could not retrieve veth. If you running on Mac, this is not supported. \n  Run the tests inside docker container as shown as example in the script run-slow-node-test.sh ")
        }
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

    /**
     * directly calls tc (traffic control) on a docker
     */
    static String runTcOnContainer(containerName, optionsArgs = "delay 100ms loss 20%") {
        def tcCommand = "tc qdisc add dev eth0 handle 10: root netem ${optionsArgs}"
        def (output, error) = runCommand("docker exec ${containerName} bash -c".tokenize() << tcCommand, null, false, true)
        return output;
    }

    static void captureLogs(String containerId, String testName) {
        Files.createDirectories(Paths.get("${System.getProperty('logs.dir')}/${testName}"));
        runCommand(['bash', '-c', "docker logs ${containerId} &> ${System.getProperty('logs.dir')}/${testName}/test${containerId.substring(0, 11)}.log"]);
    }

    static String[] generateUniverseValidators(int numNodes) {
        String[] exportVars, error
        if (isRunningOnWindows()) {
            //exportVars = TempUniverseCreator.getHardcodedUniverse(); TODO a bit weird but this helps development on windows
            throw new RuntimeException("For these tests to run on windows, you need to find a way to provide a universe.")
        }
        String gradlewPath = System.getenv('CORE_DIR')
        (exportVars, error) = runCommand("${gradlewPath}/gradlew -P validators=${numNodes} :radixdlt:clean :radixdlt:generateDevUniverse",
                null, false, true, "${System.getenv('CORE_DIR')}/radixdlt-core/radixdlt");
        String[] envVars = exportVars
                .findAll({ it.contains("export") })
                .collect({ it.replaceAll("export", "") })
        if (envVars.size() > 0)
            return envVars
        else
            throw new Exception("Universe environment variables weren't generated")
    }

    static void cleanCoreGradleOutput() {
        String gradlewPath = System.getProperty("user.dir")
        runCommand("${gradlewPath}/gradlew :radixdlt:clean", null, true, true, "${System.getenv('CORE_DIR')}");
    }

    static String getNodeValidator(String[] allEnvVariables, options) {

        return allEnvVariables.find { it.contains("RADIXDLT_VALIDATOR_${options["nodeIndex"]}_PRIVKEY") }.split("KEY=")[1]
    }

    static String getUniverse(String[] allEnvVariables) {
        return allEnvVariables.find { it.contains("RADIXDLT_UNIVERSE=") }.split("UNIVERSE=")[1]
    }

    static boolean testRunningOnDocker() {
        def present = Optional.ofNullable(System.getenv("TEST_NETWORK")).isPresent()
        return present
    }

    static String stopContainer(String containerName) {
        def (output, error) = runCommand("docker stop " + containerName);
        if (error) {
            throw new RuntimeException("Could not stop container '" + containerName + "' because: " + error.toString())
        } else {
            return output;
        }
    }

    static void startContainer(String containerName) {
        def (output, error) = runCommand("docker start " + containerName);
        if (error) {
            throw new RuntimeException("Could not start container '" + containerName + "' because: " + error.toString())
        }
    }

    static boolean isRunningOnWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows")
    }

}
