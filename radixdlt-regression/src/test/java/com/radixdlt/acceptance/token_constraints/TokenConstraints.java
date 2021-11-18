package com.radixdlt.acceptance.token_constraints;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.assertions.Assertions;
import com.radixdlt.test.RadixNetworkTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

public class TokenConstraints extends RadixNetworkTest {

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1, Amount.ofTokens(110));
    }

    @Then("I cannot create a token with symbol {string}")
    public void i_cannot_create_a_token_with_symbol(String symbol) {
        Assertions.runExpectingRadixApiException(() ->
            account1.mutableSupplyToken(symbol, "name", "description", "http://icon.com", "http://token.com"),
            "invalid token symbol: " + symbol);
    }

    @Then("I cannot create a token with token info url {string}")
    public void i_cannot_create_a_token_with_token_info_url(String tokenInfoUrl) {
        Assertions.runExpectingRadixApiException(() ->
            account1.mutableSupplyToken("symbol", "name", "description", "http://icon.com", tokenInfoUrl),
            "not a valid URL");
    }

    @Then("I cannot create a token with an icon rul {string}")
    public void i_cannot_create_a_token_with_an_icon_url(String iconUrl) {
        Assertions.runExpectingRadixApiException(() ->
            account1.mutableSupplyToken("symbol", "name", "description", iconUrl, "http://token.com"),
            "not a valid URL");
    }

}
