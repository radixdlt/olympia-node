package utils

import com.radixdlt.test.StaticClusterNetwork

import java.nio.file.Files
import java.nio.file.Paths

class EphemeralNetworkCreator {

    String terraformImage, keyVolume, ansibleImage
    List<String> tf_Opts
    int totalNumofNodes
    private String terraformSecretsDir = "/terraform/ssh"
    private String ansibleSecretsDir = "/ansible/ssh"
    private String ansibleWorkDir = "/ansible/playbooks"
    private String autoApprove = "-auto-approve"
    private List<String> hosts
    private String TF_VAR_nodes_10= " { node_1, node_2, node_3, node_4  ,node_5 , node_6, node_7 , node_8 , node_9, node_10}"
    private String TF_VAR_nodes_4= "'{ node_1={}, node_2={}, node_3={}, node_4={} }'"

    public final static String ENV_SSH_IDENTITY = "SSH_IDENTITY"
    public final static String ENV_SSH_IDENTITY_PUB = "SSH_IDENTITY_PUB"
    public final static String ENV_AWS_CREDENTIAL = "AWS_CREDENTIAL"
    public final static String ENV_GCP_CREDENTIAL = "GCP_CREDENTIAL"
    public final static String ENV_CORE_TAG = "CORE_TAG"
    public final static String ENV_TESTNET_NAME = "TESTNET_NAME"
    public final static String ENV_LOG_LEVEL = "LOG_LEVEL"


    private EphemeralNetworkCreator(String terraformImage,
                                    String keyVolume,
                                    List<String> tf_Opts,
                                    String ansibleImage){
        this.terraformImage = terraformImage
        this.ansibleImage = ansibleImage
        this.keyVolume = keyVolume
        this.tf_Opts = tf_Opts
    }

    void copyToTFSecrets(String fileLocation, String sshDestinationFileName = "testnet") {
        def output,error
        (output,error) = CmdHelper.runCommand("docker container create -v ${keyVolume}:${terraformSecretsDir} curlimages/curl:7.70.0")
        CmdHelper.runCommand("docker cp ${fileLocation} ${output[0]}:${terraformSecretsDir}/${sshDestinationFileName}")
        CmdHelper.runCommand("docker rm -f ${output[0]}")
    }

    void copyToAnsibleSecrets(String fileLocation, String sshDestinationFileName = "testnet") {
        def output,error
        (output,error) = CmdHelper.runCommand("docker container create -v ${keyVolume}:${ansibleSecretsDir} curlimages/curl:7.70.0")
        CmdHelper.runCommand("docker cp ${fileLocation} ${output[0]}:${ansibleSecretsDir}/${sshDestinationFileName}")
        CmdHelper.runCommand("docker rm -f ${output[0]}")
    }

    void setTotalNumberOfNodes(int totalNumberOfNodes) {
        this.totalNumofNodes = totalNumberOfNodes
    }

    void pullImage() {
        CmdHelper.runCommand("docker pull ${terraformImage}")
        CmdHelper.runCommand("docker pull ${ansibleImage}")
    }

    void setup(){
        def runString ="bash -c".tokenize() << (
                "docker run --rm -v " +
                        "${keyVolume}:${terraformSecretsDir} " +
                        "--name node-terraform ${terraformImage} apply " +
                        "-var-file='${totalNumofNodes}-nodes.tfvars' " +
                        "${autoApprove}  " as String)
        CmdHelper.runCommand(runString,[] as String[],true)

    }

    void plan(){

        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${terraformSecretsDir} " +
                        "--name node-terraform ${terraformImage} plan " +
                        "-var-file='${totalNumofNodes}-nodes.tfvars'" as String)
        CmdHelper.runCommand(runString,[] as String[],true)
    }

    void teardown(){

        def runString ="bash -c".tokenize() << (
                "docker run --rm -v ${keyVolume}:${terraformSecretsDir} " +
                        "--name node-terraform ${terraformImage} destroy " +
                        "-var-file='${totalNumofNodes}-nodes.tfvars' " +
                        "${autoApprove} " as String)
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
                "-v ${keyVolume}:${ansibleSecretsDir} " ,
                "-i aws-inventory")
    }

    void captureLogs(String networkGroup,String testName) {

        def output,error
        def fetchLogsCmd = "bash -c".tokenize() << (
                "docker run --rm  -v ${keyVolume}:${ansibleSecretsDir} -v ${keyVolume}-logs:${ansibleWorkDir}/target " +
                        "${ansibleImage} " +
                        "check.yml -i aws-inventory  " +
                        "--limit ${networkGroup} -t fetch_logs " as String)
        (output,error)=CmdHelper.runCommand(fetchLogsCmd)
        Files.createDirectories(Paths.get("${System.getProperty('logs.dir')}/logs/${testName}"));

        (output,error) = CmdHelper.runCommand("docker container create -v ${keyVolume}-logs:${ansibleWorkDir}/target curlimages/curl:7.70.0")
        CmdHelper.runCommand("docker cp ${output[0]}:${ansibleWorkDir}/target/ ${System.getProperty('logs.dir')}/logs/${testName}")
        CmdHelper.runCommand("docker rm -f ${output[0]}")

    }

    void volumeCleanUp(){
        CmdHelper.runCommand("docker volume rm -f ${keyVolume} ${keyVolume}-logs ")

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

        Builder withTerraformOptions(List<String> tf_opts){
            this.tf_Opts = tf_opts
            return this
        }

        EphemeralNetworkCreator build() {
            Objects.requireNonNull(this.terraformImage)
            Objects.requireNonNull(this.keyVolume)
            Objects.requireNonNull(this.ansibleImage)
            return new EphemeralNetworkCreator(this.terraformImage, this.keyVolume, this.tf_Opts, this.ansibleImage)
        }
    }
}
