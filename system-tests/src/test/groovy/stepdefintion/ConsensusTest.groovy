package stepdefintion

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import utils.CmdHelper
import utils.Generic

import static utils.CmdHelper.node
import static utils.CmdHelper.runCommand
import static utils.Generic.listToDelimitedString


class ConsensusTest {

    Map SetupOptions
    String sentMessageAID

    @Given('^I have (.*) network with (.*) nodes and quorumsize of (.*) nodes$')
    void iHaveNetworkWithThreeNodes(String networkName, int numberOfNodes, int quorumSize) {

        SetupOptions = CmdHelper.getDockerOptions(numberOfNodes, quorumSize)
        CmdHelper.removeAllDockerContainers()
        runCommand("docker network rm ${networkName}", null, true)
        runCommand("docker network create ${networkName}")
        String[] dockerEnv
        String dockerCommand
        SetupOptions.keySet().each {
            setupOptions[it].network = networkName
            (dockerEnv, dockerCommand) = node(setupOptions[it])
            runCommand("${dockerCommand}", dockerEnv)
        }
        CmdHelper.checkNGenerateKey()
    }


    @When("I send a message to first node one")
    void iSendAMessage() {

        println "User directory ${System.getProperty("user.dir")}"
        List sendMessageOptions = [
                "send-message",
                "--keystore=${Generic.keyStorePath()}",
                "--password=test123",
                "--address=JHJEBjTsEcmGKzrvTVwQ4BpDmwRvYYYjbehXdSnpx8C5kCdUNEK",
                "--message=hello"
        ]
        //TODO enhance this sleep to wait for system to be up
        Thread.sleep(5000)


        def sendMessageCmd = CmdHelper.radixCliCommand(sendMessageOptions)

        List<String[]> output, error
        (output, error) = runCommand(sendMessageCmd,
                ["RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:${setupOptions["core1"].hostPort}"] as String[], true)
        def filtered = output.find({ it.contains("AtomID") })
        def extracted = filtered =~ /AtomID of resulting atom : (.*)/
        sentMessageAID = extracted[0][1]
    }



    @Then("corresponding atom of the message should be available on atom store of all nodes")
    void correspondingAtomOfTheMessageShouldBeAvailableOnAtomStoreOfAllNodes() {
        println "Checking Atom ${sentMessageAID}"

        List getAtomsOptions = [
                "get-stored-atoms",
                "--keystore=${Generic.keyStorePath()}",
                "--password=test123",
        ]

        def sendMessageCmd = CmdHelper.radixCliCommand(getAtomsOptions)
        SetupOptions.keySet().each {
            List<String[]> output, error
            (output, error) = runCommand(sendMessageCmd,
                    ["RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:${setupOptions[it].hostPort}"] as String[], true)
            assert output.find({ it.contains(sentMessageAID) }) != null
        }
    }
}
