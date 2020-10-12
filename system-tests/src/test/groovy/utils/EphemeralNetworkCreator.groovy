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

    String terraformImage, keyVolume, ansibleImage
    List<String> tf_Opts
    int totalNumofNodes
    private String terraformSecretsDir = "/terraform/ssh"
    private String ansibleSecretsDir = "/ansible/ssh"
    private String autoApprove = "-auto-approve"
    private List<String> hosts;

    public final static String ENV_SSH_IDENTITY = "SSH_IDENTITY";
    public final static String ENV_SSH_IDENTITY_PUB = "SSH_IDENTITY_PUB";
    public final static String ENV_AWS_CREDENTIAL = "AWS_CREDENTIAL";
    public final static String ENV_GCP_CREDENTIAL = "GCP_CREDENTIAL";
    public final static String ENV_CORE_TAG = "CORE_TAG";
    public final static String ENV_TESTNET_NAME = "TESTNET_NAME";


    private EphemeralNetworkCreator(String terraformImage,
                                    String keyVolume,
                                    List<String> tf_Opts,
                                    int totalNumOfnodes,
                                    String ansibleImage){
        this.terraformImage = terraformImage
        this.ansibleImage = ansibleImage
        this.keyVolume = keyVolume
        this.tf_Opts = tf_Opts
        this.totalNumofNodes = totalNumOfnodes
    }
    void copyToTFSecrets(String fileLocation,  String sshDestinationFileName="testnet" ) {
        CmdHelper.runCommand("docker container create --name dummy -v ${keyVolume}:${terraformSecretsDir} curlimages/curl:7.70.0")
        CmdHelper.runCommand("docker cp ${fileLocation} dummy:${terraformSecretsDir}/${sshDestinationFileName}")
        CmdHelper.runCommand("docker rm -f dummy")
    }

    void copyToAnsibleSecrets(String fileLocation,  String sshDestinationFileName="testnet" ) {
        CmdHelper.runCommand("docker container create --name dummy -v ${keyVolume}:${ansibleSecretsDir} curlimages/curl:7.70.0")
        CmdHelper.runCommand("docker cp ${fileLocation} dummy:${ansibleSecretsDir}/${sshDestinationFileName}")
        CmdHelper.runCommand("docker rm -f dummy")
    }

    void pullImage() {
        CmdHelper.runCommand("docker pull ${terraformImage}")
        CmdHelper.runCommand("docker pull ${ansibleImage}")
    }

    void setup(){
        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${terraformSecretsDir} " +
                        "--name node-terraform ${terraformImage} apply ${autoApprove}  " as String)
        CmdHelper.runCommand(runString,[] as String[],true)

    }

    void plan(){

        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${terraformSecretsDir} " +
                        "--name node-terraform ${terraformImage} plan " as String)
        CmdHelper.runCommand(runString,[] as String[],true)
    }

    void teardown(){

        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${terraformSecretsDir} " +
                        "--name node-terraform ${terraformImage} destroy ${autoApprove} " as String)
        CmdHelper.runCommand(runString, [] as String[],true)
    }

    String getHosts(){
        List<String[]> output, error
        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${terraformSecretsDir} " +
                        "--name node-terraform ${terraformImage} output | grep node_ | cut -d'\"' -f 4" as String)
        (hosts, error)= CmdHelper.runCommand(runString, [] as String[],true)
    }

    List<String> deploy(List<String> dockerOptions, List<String> ansibleOptions){
        def output,error
        def runString = "bash -c".tokenize() << (
                "docker run --rm  -e RADIXDLT_UNIVERSE -v ${keyVolume}:${ansibleSecretsDir} " +
                        "${ansibleImage} " +
                        "deploy.yml " +
                        " ${Generic.listToDelimitedString(ansibleOptions," ")}" as String)
        (output,error)=CmdHelper.runCommand(runString,["RADIXDLT_UNIVERSE=${System.getenv('RADIXDLT_UNIVERSE')}"] as String[])
        return error
    }

    List<String> nodesToBringdown(String nodeToBringdown){
        def output,error
        def runString = "bash -c".tokenize() << (
                "docker run --rm  -v ${keyVolume}:${ansibleSecretsDir} " +
                        "${ansibleImage} " +
                        "control-node.yml -i aws-inventory  " +
                        "--limit ${nodeToBringdown} -t down " as String)
        (output,error)=CmdHelper.runCommand(runString)
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
        String terraformImage,ansibleImage, keyVolume
        List<String> tf_Opts
        int totalNumofNodes

        private Builder() {
        }

        Builder withTerraformImage(String terraformImage) {
            this.terraformImage = terraformImage
            return this
        }
        Builder withAnsibleImage(String ansibleImage) {
            this.ansibleImage = ansibleImage
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
            return new EphemeralNetworkCreator(this.terraformImage, this.keyVolume, this.tf_Opts, this.totalNumofNodes,this.ansibleImage)
        }
    }
}
