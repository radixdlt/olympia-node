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

package stepdefintion

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import utils.CmdHelper
import utils.Generic

import static utils.CmdHelper.node
import static utils.CmdHelper.runCommand

class Consensus {

    Map SetupOptions
    String sentMessageAID
    List sentMessages

    @Given('^I have (.*) network with (.*) nodes and quorumsize of (.*) nodes$')
    void i_have_network_with_n_nodes_and_quorumsize_of_n_nodes(String networkName, int numberOfNodes, int quorumSize) {

        SetupOptions = CmdHelper.getDockerOptions(numberOfNodes, true)
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
    void i_send_a_message_to_first_node_one() {

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

    @When('^I send sequence of (.*) message to first node one$')
    void "i_send_sequence_of_n_message_to_first_node_one"(int numberOfMessages) {

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
        sentMessages = (1..numberOfMessages).collect({
            List<String> output, error
            (output, error) = runCommand(sendMessageCmd,
                    ["RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:${setupOptions["core1"].hostPort}"] as String[], true)
            def filtered = output.find({ it.contains("AtomID") })
            def extracted = filtered =~ /AtomID of resulting atom : (.*)/
            return extracted[0][1]
        })

    }

    @Then("all the atomIDs are committed on all nodes")
    void all_the_atomIDs_are_committed_on_all_nodes() {
        List getAtomsOptions = [
                "get-stored-atoms",
                "--keystore=${Generic.keyStorePath()}",
                "--password=test123",
        ]
        def sendMessageCmd = CmdHelper.radixCliCommand(getAtomsOptions)
        SetupOptions.keySet().each {
            List<String> output, error
            (output, error) = runCommand(sendMessageCmd,
                    ["RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:${setupOptions[it].hostPort}"] as String[], true)
            List<String> actualSequenceFromNode =  output[1..output.size()-1]
            assert actualSequenceFromNode.toSet() == sentMessages.toSet()
        }
    }
    @Then("corresponding atom of the message should be available on atom store of all nodes")
    void corresponding_atom_of_the_message_should_be_available_on_atom_store_of_all_nodes() {
        println "Checking Atom ${sentMessageAID}"

        List getAtomsOptions = [
                "get-stored-atoms",
                "--keystore=${Generic.keyStorePath()}",
                "--password=test123",
        ]

        def sendMessageCmd = CmdHelper.radixCliCommand(getAtomsOptions)
        SetupOptions.keySet().each {
            List<String> output, error
            (output, error) = runCommand(sendMessageCmd,
                    ["RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:${setupOptions[it].hostPort}"] as String[], true)
            assert output.find({ it.contains(sentMessageAID) }) != null
        }
    }
}
