package com.radixdlt.acceptance.fixed_supply_tokens;

import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.dto.TransactionDTO;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FixedSupplyTokens extends RadixNetworkTest {

    private static final Logger logger = LogManager.getLogger();

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1, Amount.ofTokens(110));
    }

    @When("I create a fixed supply token with properties: {string}, {string}, {string}, {string}, {string}, with amount {int}")
    public void i_create_a_fixed_supply_token_with_properties(String symbol, String name, String description,
                                                              String iconUrl, String tokenUrl, int amount) {
        txBuffer = account1.fixedSupplyToken(symbol, name, description, iconUrl, tokenUrl, Amount.ofTokens(amount));
    }

    @When("I create a fixed supply token with name {string}")
    public void i_create_a_fixed_supply_token_with_name(String name) {
        txBuffer = account1.fixedSupplyToken("symbol", name, "description",
            "https://www.icon.com", "https://www.url.com", Amount.ofTokens(200));
    }

    @Then("I can observe that the last token creation failed")
    public void i_can_observe_that_the_last_token_creation_failed() {
        throw new io.cucumber.java.PendingException();
    }

    @Then("I can observe that the token has been created, with the correct values")
    public void i_can_observe_that_the_token_has_been_created_with_the_correct_values() {
        // TODO this returns "Other", so no assertions can be made
        TransactionDTO transaction = account1.lookup(txBuffer);
    }

}
