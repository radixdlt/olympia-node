package com.radixdlt.acceptance.fixed_supply_tokens;

import com.radixdlt.TokenCreationProperties;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.assertions.Assertions;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.utils.TestingUtils;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class FixedSupplyTokens extends RadixNetworkTest {

    private TokenCreationProperties tokenProperties;

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1, Amount.ofTokens(110));
    }

    @When("I create a fixed supply token with properties: {string}, {string}, {string}, {string}, {string}, {int} total supply")
    public void i_create_a_fixed_supply_token_with_properties(String symbol, String name, String description,
                                                              String iconUrl, String tokenInfoUrl, int amount) {
        this.tokenProperties = new TokenCreationProperties(symbol, name, description, iconUrl, tokenInfoUrl, amount);
        txBuffer = account1.fixedSupplyToken(symbol, name, description, iconUrl, tokenInfoUrl, Amount.ofTokens(amount));
    }

    @When("I create a fixed supply token with a total supply of {int}")
    public void i_create_a_fixed_supply_token_with_a_supply_of(int totalSupplyTokens) {
        var symbol = "fsymbol";
        var name = "fname";
        var description = "acceptance test token";
        var iconUrl = "http://icon.com";
        var tokenInfoUrl = "http://token.com";
        this.tokenProperties = new TokenCreationProperties(symbol, name, description, iconUrl, tokenInfoUrl, totalSupplyTokens);
        txBuffer = account1.fixedSupplyToken(symbol, name, description, iconUrl, tokenInfoUrl, Amount.ofTokens(totalSupplyTokens));
    }

    @Then("I can observe that the token has been created, with the correct values")
    public void i_can_observe_that_the_token_has_been_created_with_the_correct_values() {
        Assertions.assertTokenProperties(account1, txBuffer, tokenProperties);
    }

    @Then("I can send {int} of my new tokens to another account")
    public void i_can_send_of_my_new_tokens_to_another_account(int tokensToTransfer) {
        var rri = TestingUtils.getRriFromAID(account1, txBuffer);
        account1.transfer(account2, Amount.ofTokens(tokensToTransfer), rri, Optional.empty());
    }

    @Then("I can observe that the token has been created, with a total supply of {int}")
    public void i_can_observe_that_the_token_has_been_created_with_a_total_supply_of(int totalSupplyTokens) {
        var tokenInfo = TestingUtils.getTokenInfoFromAID(account1, txBuffer);
        assertEquals(Amount.ofTokens(totalSupplyTokens).toSubunits(), tokenInfo.getCurrentSupply());
    }

    @Then("I cannot transfer more than the total supply")
    public void i_cannot_transfer_more_than_the_total_supply() {
        var rri = TestingUtils.getRriFromAID(account1, txBuffer);
        var totalSupplyPlusOneToken = Amount.ofTokens(tokenProperties.getAmount() + 1);
        try {
            account1.transfer(account2, totalSupplyPlusOneToken, rri, Optional.empty());
        } catch (RadixApiException e) {
            // sadly we don't get a more specific exception
            assertEquals("Transaction submission failed: Not enough balance for transfer.", e.getMessage());
        }
    }

}
