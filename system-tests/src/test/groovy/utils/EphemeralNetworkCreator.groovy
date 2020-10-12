package utils

import com.radixdlt.test.StaticClusterNetwork

/*
 docker run -it  --rm
 -e AWS_SECRET_ACCESS_KEY
 -e AWS_ACCESS_KEY_ID
 -v /Users/shambu/project/radixdlt-iac/projects/testnet/ssh:/terraform/ssh
  eu.gcr.io/lunar-arc-236318/node-terraform:task-RPNV1-315-10nodes-1-outofsynchrony-a8939e6
* */
class EphemeralNetworkCreator {

    String terraformImage, keyVolume
    List<String> tf_Opts
    int totalNumofNodes
    private String sshDestinationLocDir = "/terraform/ssh"
    private String sshDestinationFileName = "testnet"
    private String autoApprove = "-auto-approve"
    private String AWS_SECRET_ACCESS_KEY,AWS_ACCESS_KEY_ID
    private List<String> hosts;

    private EphemeralNetworkCreator(String terraformImage,
                                    String keyVolume,
                                    List<String> tf_Opts,
                                    int totalNumOfnodes ){
        this.terraformImage = terraformImage
        this.keyVolume = keyVolume
        this.tf_Opts = tf_Opts
        this.totalNumofNodes = totalNumOfnodes
        this.AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY")
        this.AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID")
    }
    void copyfileToNamedVolume(String fileLocation, String sshDestinationLocDir="/terraform/ssh", String sshDestinationFileName="testnet" ) {
        CmdHelper.runCommand("docker container create --name dummy -v ${keyVolume}:${sshDestinationLocDir} curlimages/curl:7.70.0")
        CmdHelper.runCommand("docker cp ${fileLocation} dummy:${sshDestinationLocDir}/${sshDestinationFileName}")
        CmdHelper.runCommand("docker rm -f dummy")
    }

    void pullImage() {
        CmdHelper.runCommand("docker pull ${terraformImage}")
    }

    void setup(){
        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${sshDestinationLocDir} " +
                        "-e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} -e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}  " +
                        "--name node-terraform ${terraformImage} apply ${autoApprove}  " as String)
        CmdHelper.runCommand(runString,[] as String[],true)

    }

    void plan(){

        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${sshDestinationLocDir} " +
                        "--name node-terraform ${terraformImage} plan " as String)
        CmdHelper.runCommand(runString,[] as String[],true)
    }

    void teardown(){

        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${sshDestinationLocDir} " +
                        "--name node-terraform ${terraformImage} destroy ${autoApprove} " as String)
        CmdHelper.runCommand(runString, [] as String[],true)
    }

    String getHosts(){
        List<String[]> output, error
        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${sshDestinationLocDir} " +
                        "--name node-terraform ${terraformImage} output | grep node_ | cut -d'\"' -f 4" as String)
        (hosts, error)= CmdHelper.runCommand(runString, [] as String[],true)
    }

    String provision(){
        //docker run --rm -v key-volume:/ansible/ssh --name node-ansible -e RADIXDLT_UNIVERSE eu.gcr.io/lunar-arc-236318/node-ansible:python3  check.yml -i aws-inventory  --limit tag_Environment_ephemeralnet --check
        def runString = "bash -c".tokenize() << (
                "docker run --rm  -v key-volume:/ansible/ssh " +
                        " eu.gcr.io/lunar-arc-236318/node-ansible:python3" +
                        " provision.yml -i aws-inventory  --limit tag_Environment_ephemeralnet"
        )
        CmdHelper.runCommand(runString,[] as String[],true)
    }
    List<String> deploy(){
        def output,error
        def runString = "bash -c".tokenize() << (
                "docker run --rm  -v key-volume:/ansible/ssh " +
                        " eu.gcr.io/lunar-arc-236318/node-ansible:python3" +
                        " deploy.yml -i aws-inventory  " +
                        "--limit tag_Environment_ephemeralnet " +
                        "-e 'core_tag=:HEAD-043ccbdc' " +
                        "-e boot_nodes=\"{{ groups['tag_Environment_ephemeralnet'] | join(',') }}\" " +
                        " -e quorum_size=10 -e consensus_start_on_boot=true"
        )
        (output,error)=CmdHelper.runCommand(runString,["RADIXDLT_UNIVERSE=${System.getenv('RADIXDLT_UNIVERSE')}"] as String[])
        return error
    }

    List<String> nodesToBringdown(String nodeToBringdown){
        def output,error
        //TODO need more parameterisation
        def runString = "bash -c".tokenize() << (
                "docker run --rm  -v key-volume:/ansible/ssh " +
                        " eu.gcr.io/lunar-arc-236318/node-ansible:python3" +
                        " control-node.yml -i aws-inventory  " +
                        "--limit ${nodeToBringdown} -t down "
        )
        (output,error)=CmdHelper.runCommand(runString,["RADIXDLT_UNIVERSE=${System.getenv('RADIXDLT_UNIVERSE')}"] as String[])
        return error
    }

    static Builder builder() {
        return new Builder()
    }

    StaticClusterNetwork getNetwork(int i) {
        return StaticClusterNetwork.clusterInfo(
                10,
                null ,
                "-i aws-inventory");
    }


    static class Builder {
        String terraformImage, keyVolume
        List<String> tf_Opts
        int totalNumofNodes

        private Builder() {
        }

        Builder withTerraformImage(String terraformImage) {
            this.terraformImage = terraformImage
            return this
        }

        Builder withKeyVolume(String keyVolume) {
            this.keyVolume = keyVolume
            return this
        }

        Builder withTotalNumofNodes(int totalNumofNodes) {
            this.totalNumofNodes = totalNumofNodes
            return this
        }

        Builder withTerraformOptions(List<String> tf_opts){
            this.tf_Opts = tf_opts
            return this
        }

        EphemeralNetworkCreator build() {
            return new EphemeralNetworkCreator(this.terraformImage, this.keyVolume, this.tf_Opts, this.totalNumofNodes)
        }
    }
}
