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

    static List<String[]> runCommand(cmd, String[] env = null, failOnError = false, logOutput=true) {

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
        process.waitFor()

        List output
        if (sout) {
            output = sout.toString().split(System.lineSeparator()).collect({ it })
            if(logOutput){
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
            (key, error) = runCommand("java -jar ${Generic.pathToCLIJar()} ${listToDelimitedString(options, ' ')}", null, true,false)
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
}
