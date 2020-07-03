package utils

class TestnetNodes {

    private static final TestnetNodes instance = new TestnetNodes();

    private TestnetNodes() {
    }


    private List nodes
    String keyVolume = "key-volume"
    private String sshDestinationLocDir = "/ansible/ssh"
    private String sshDestinationFileName = "testnet"
    String ansibleImage = "eu.gcr.io/lunar-arc-236318/node-ansible:latest"

    static TestnetNodes getInstance() {
        return instance
    }

    String getNodesURls() {
        if (nodes == null) {
            fetchNodes()
        }
        return nodes.inject("", {
            str, node ->
                if (this.nodes.first() == node)
                    return "${str}https://${node}"
                return "${str},https://${node}"
        })
    }

    private fetchNodes() {
        String clusterName = Optional.ofNullable(System.getenv("TESTNET_NAME")).orElse("testnet_2")

        println "node information not avaliable"
        String sshKeylocation = Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa");
        CmdHelper.runCommand("docker container create --name dummy -v ${keyVolume}:${sshDestinationLocDir} curlimages/curl:7.70.0")
        CmdHelper.runCommand("docker cp ${sshKeylocation} dummy:${sshDestinationLocDir}/${sshDestinationFileName}")
        CmdHelper.runCommand("docker rm -f dummy")
        def output, error
        (output, error) = CmdHelper.runCommand("docker run --rm  -v ${keyVolume}:/ansible/ssh --name node-ansible ${ansibleImage}  "
                + "check.yml --limit ${clusterName} --list-hosts")
        nodes = output.findAll({
            !(it.contains("play") || it.contains("pattern") || it.contains("hosts") || it == "")
        }).collect({ it.trim() })
    }
}
