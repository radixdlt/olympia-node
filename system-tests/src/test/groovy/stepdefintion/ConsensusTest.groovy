package stepdefintion

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When


class ConsensusTest {

    @Given("I have network with three nodes")
    void iHaveNetworkWithThreeNodes() {
        println "Currently running mannually"
    }

    @When("I send a message")
    void iSendAMessage() {
    }

    @Then("corresponding atom of the message should be available on atom store of all nodes")
    void correspondingAtomOfTheMessageShouldBeAvailableOnAtomStoreOfAllNodes() {
    }
}
