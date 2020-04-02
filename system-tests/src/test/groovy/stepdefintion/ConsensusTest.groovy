package stepdefintion

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import utils.Generic

import static utils.CmdHelper.node
import static utils.CmdHelper.runCommand
import static utils.Generic.listToDelimitedString


class ConsensusTest {

    Map dockerOptions = [
            core1Options: [
                    nodeName         : "core1",
                    quorumSize       : "3",
                    remoteSeeds      : ["core2", "core3"],
                    hostPort         : 1080,
                    rmiPort          : 9011,
                    socketAddressPort: 50505
            ],
            core2Options: [
                    nodeName         : "core2",
                    quorumSize       : "3",
                    remoteSeeds      : ["core1", "core3"],
                    hostPort         : 1081,
                    rmiPort          : 9012,
                    socketAddressPort: 50506

            ],
            core3Options: [
                    nodeName         : "core3",
                    quorumSize       : "3",
                    remoteSeeds      : ["core1", "core3"],
                    hostPort         : 1083,
                    rmiPort          : 9013,
                    socketAddressPort: 50507

            ]]
    String sentMessageAID

    @Given('^I have (.*) network with (.*) nodes$')
    void iHaveNetworkWithThreeNodes(String networkName, int numberOfNodes) {
        ["core1", "core2", "core3"].each { it -> runCommand("docker rm -f ${it}") }
        runCommand("docker network rm ${networkName}")
        runCommand("docker network create ${networkName}")
        String[] dockerEnv
        String dockerCommand
        dockerOptions.keySet().each {
            dockerOptions[it].network = networkName
            (dockerEnv, dockerCommand) = node(dockerOptions[it])
            runCommand("${dockerCommand}", dockerEnv)
        }
    }

    @When("I send a message to first node one")
    void iSendAMessage() {

        println "User directory ${System.getProperty("user.dir")}"
        List sendMessageOptions = [
                "--keystore=${System.getProperty('user.dir')}/src/test/resources/keystore/shambu-key.json",
                "--password=shambu",
                "--address=JHJEBjTsEcmGKzrvTVwQ4BpDmwRvYYYjbehXdSnpx8C5kCdUNEK",
                "--message=hello"
        ]
        Thread.sleep(5000)
        def sendMessageCmd = "java -jar target/cli/radixdlt-cli-all.jar send-message ${listToDelimitedString(sendMessageOptions, ' ')}"
        List<String[]> output, error
        (output, error) = runCommand(sendMessageCmd,
                ["RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:${dockerOptions.core1Options.hostPort}"] as String[], true)
        def filtered = output.find({ it.contains("AtomID") })
        def extracted = filtered =~ /AtomID of resulting atom : (.*)/
        sentMessageAID = extracted[0][1]
    }

    @Then("corresponding atom of the message should be available on atom store of all nodes")
    void correspondingAtomOfTheMessageShouldBeAvailableOnAtomStoreOfAllNodes() {
        println "Checking Atom ${sentMessageAID}"

        List getAtomsOptions = [
                "--keystore=${System.getProperty('user.dir')}/src/test/resources/keystore/shambu-key.json",
                "--password=shambu",
        ]

        def sendMessageCmd = "java -jar target/cli/radixdlt-cli-all.jar get-stored-atoms ${listToDelimitedString(getAtomsOptions, ' ')}"
        dockerOptions.keySet().each {
            List<String[]> output, error
            (output, error) = runCommand(sendMessageCmd,
                    ["RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:${dockerOptions[it].hostPort}"] as String[], true)
            assert output.find({ it.contains(sentMessageAID) }) != null
        }
    }
}
