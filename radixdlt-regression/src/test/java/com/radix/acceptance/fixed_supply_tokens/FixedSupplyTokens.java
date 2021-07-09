package com.radix.acceptance.fixed_supply_tokens;

import com.radix.acceptance.AcceptanceTest;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;

import java.util.List;

public class FixedSupplyTokens extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
    }

    @When("I create a fixed supply token with properties: {string}, {string}, {string}, {string}, {string}, {string}, with amount {int}")
    public void i_create_a_fixed_supply_token_with_properties(String rri, String symbol, String name, String description,
                                                              String iconUrl, String tokenUrl, int amount) {
        txBuffer = account1.fixedSupplyToken(rri, symbol, name, description, iconUrl, tokenUrl, Amount.ofTokens(amount));
    }

    @When("I create a fixed supply token with rri {string}")
    public void i_create_a_fixed_supply_token_with_rri(String rri) {
        txBuffer = account1.fixedSupplyToken(rri, "symbol", "name", "description",
            "www.icon-url.com", "www.token-url.com", Amount.ofTokens(200));
    }

    @Then("I can observe that the token has been created, with the correct values")
    public void i_can_observe_that_the_token_has_been_created_with_the_correct_values() {
        TransactionDTO transaction = account1.lookup(txBuffer);
        System.out.println(transaction);
    }

}
