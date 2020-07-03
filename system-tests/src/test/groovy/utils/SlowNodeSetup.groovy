package utils

class SlowNodeSetup {
    String ansibleImage, keyVolume, clusterName, playbook
    int numOfSlowNodes
    private String sshDestinationLocDir = "/ansible/ssh"
    private String sshDestinationFileName = "testnet"


    private SlowNodeSetup(String ansibleImage, String keyVolume, String clusterName, String playbook, int numOfSlowNodes) {
        this.ansibleImage = ansibleImage
        this.keyVolume = keyVolume
        this.clusterName = clusterName
        this.playbook = playbook
        this.numOfSlowNodes = numOfSlowNodes
    }

    void copyfileToNamedVolume(String fileLocation) {
        CmdHelper.runCommand("docker container create --name dummy -v ${keyVolume}:${sshDestinationLocDir} curlimages/curl:7.70.0")
        CmdHelper.runCommand("docker cp ${fileLocation} dummy:${sshDestinationLocDir}/${sshDestinationFileName}")
        CmdHelper.runCommand("docker rm -f dummy")
    }

    void pullImage() {
        CmdHelper.runCommand("docker pull ${ansibleImage}")
    }

    void setup() {

        (1..numOfSlowNodes).each {
            CmdHelper.runCommand("docker run --rm  -v ${keyVolume}:/ansible/ssh --name node-ansible ${ansibleImage}  "
                    + "${playbook} -vv  --limit ${clusterName}[${it - 1}] -t setup")
        }

    }

    void tearDown() {
        (1..numOfSlowNodes).each {
            CmdHelper.runCommand("docker run --rm  -v ${keyVolume}:${sshDestinationLocDir} --name node-ansible ${ansibleImage}  "
                    + "${playbook} -vv  --limit ${clusterName}[${it - 1}] -t teardown")
        }
        CmdHelper.runCommand("docker volume rm -f ${keyVolume}")

    }

    static Builder builder() {
        return new Builder()
    }

    static class Builder {
        String ansibleImage, keyVolume, clusterName, playbook
        int numOfSlowNodes

        private Builder() {
        }

        Builder withAnsibleImage(String ansibleImage) {
            this.ansibleImage = ansibleImage
            return this
        }

        Builder withKeyVolume(String keyVolume) {
            this.keyVolume = keyVolume
            return this
        }

        Builder usingCluster(String clusterName) {
            this.clusterName = clusterName
            return this
        }

        Builder usingAnsiblePlaybook(String playbook) {
            this.playbook = playbook
            return this
        }

        Builder nodesToSlowDown(int numOfSlowNodes) {
            this.numOfSlowNodes = numOfSlowNodes
            return this
        }

        SlowNodeSetup build() {
            return new SlowNodeSetup(this.ansibleImage, this.keyVolume, this.clusterName, this.playbook, this.numOfSlowNodes)
        }
    }


}


