package utils

class CmdHelper {
    static List runCommand(cmd, String[] env = null, failOnError = false) {

        Thread.sleep(1000)
        def sout = new StringBuffer()
        def serr = new StringBuffer()
        def process
        println "------Executing command ${cmd}-----"
        if (env) {
            println "------Environment variables ${env}-----"
            process = cmd.execute(env as String[], null)
        } else
            process = cmd.execute()

        process.consumeProcessOutput(sout, serr)
        process.waitFor()

        List outPut, error
        if (sout) {
            outPut = sout.collect { it }
            println "-----------Output---------"
            sout.each { println it }
        }

        if (serr) {
            println "-----------error---------"

            serr.each { println it }
            error = serr.collect({ it })
            if (failOnError) {
                throw new Exception(error.toString())
            }
        }
        return [outPut, error]
    }


    static List node(options) {
        String[] env = ["JAVA_OPTS=-server -Xms2g -Xmx2g -Djava.security.egd=file:/dev/urandom -Dcom.sun.management.jmxremote.port=${options.rmiPort} -Dcom.sun.management.jmxremote.rmi.port=${options.rmiPort} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost -agentlib:jdwp=transport=dt_socket,address=${options.socketAddressPort},suspend=n,server=y",
                        "RADIXDLT_NETWORK_SEEDS_REMOTE=${Generic.listToDelimitedString(options.remoteSeeds)}",
                        "RADIXDLT_CONSENSUS_FIXED_QUORUM_SIZE=${options.quorumSize}",

        ]
        String dockerContainer = "docker run -d " +
                "-e RADIXDLT_NETWORK_SEEDS_REMOTE " +
                "--name ${options.nodeName}  " +
                "-e RADIXDLT_CONSENSUS_FIXED_QUORUM_SIZE " +
                "-e JAVA_OPTS " +
                "-l com.radixdlt.roles='core' " +
                "-p ${options.hostPort}:8080 " +
                "--network ${options.network} " +
                "radixdlt/radixdlt-core:develop"
        return [env as String[], dockerContainer]
    }

}
