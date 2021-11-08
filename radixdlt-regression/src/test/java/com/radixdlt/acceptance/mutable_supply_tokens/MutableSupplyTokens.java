package com.radixdlt.acceptance.mutable_supply_tokens;

import com.radixdlt.TokenCreationProperties;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.assertions.Assertions;
import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.utils.TestingUtils;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class MutableSupplyTokens extends RadixNetworkTest {

    private TokenCreationProperties tokenProperties;

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1, Amount.ofTokens(110));
    }

    @Given("I have an account with {int} XRD at a suitable Radix network")
    public void i_have_an_account_with_xrd_at_a_suitable_radix_network(int tokens) {
        faucet(account1, Amount.ofTokens(tokens));
    }

    @When("I create a mutable supply token")
    public void i_create_a_mutable_supply_token() {
        var symbol = "msymbol";
        var name = "mname";
        var description = "acceptance test token";
        var iconUrl = "http://icon.com";
        var tokenInfoUrl = "http://token.com";
        this.tokenProperties = new TokenCreationProperties(symbol, name, description, iconUrl, tokenInfoUrl);
        txBuffer = account1.mutableSupplyToken(symbol, name, description, iconUrl, tokenInfoUrl);
    }

    @When("I create a mutable supply token with properties: {string}, {string}, {string}, {string}, {string}")
    public void i_create_a_fixed_supply_token_with_properties(String symbol, String name, String description, String iconUrl, String tokenInfoUrl) {
        this.tokenProperties = new TokenCreationProperties(symbol, name, description, iconUrl, tokenInfoUrl);
        txBuffer = account1.mutableSupplyToken(symbol, name, description, iconUrl, tokenInfoUrl);
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

    @Then("I can mint {int} of this token")
    public void i_can_mint_of_this_token(int tokensToMint) {
        var rri = TestingUtils.getRriFromAID(account1, txBuffer);
        account1.mint(Amount.ofTokens(tokensToMint), rri, Optional.empty());
    }

    @Then("I can burn {int} of this token")
    public void i_can_burn_of_this_token(int tokensToBurn) {
        var rri = TestingUtils.getRriFromAID(account1, txBuffer);
        account1.burn(Amount.ofTokens(tokensToBurn), rri, Optional.empty());
    }

    @Then("the total supply should be {int}")
    public void the_total_supply_should_be(int expectedTotalSupply) {
        var currentSupplyAmount = Amount.ofSubunits(TestingUtils.getTokenInfoFromAID(account1, txBuffer).getCurrentSupply());
        var expectedTotalSupplyAmount = Amount.ofTokens(expectedTotalSupply);
        assertEquals(expectedTotalSupplyAmount.toSubunits(), currentSupplyAmount.toSubunits());
    }

}
