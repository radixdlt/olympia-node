package com.radixdlt.acceptance.token_transfer;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.RadixNetworkTest;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TokenTransfer extends RadixNetworkTest {

    private static final Logger logger = LogManager.getLogger();

    private Amount amountBuffer;

    @Given("I have two accounts with funds at a suitable Radix network")
    public void i_have_two_accounts_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
        faucet(account2);
    }

    @Given("I have an account with {int} XRD at a suitable Radix network")
    public void i_have_an_account_with_xrd_at_a_suitable_radix_network(int tokens) {
        faucet(account1, Amount.ofTokens(tokens));
    }

    @When("I transfer {int} XRD to myself")
    public void i_transfer_xrd_to_myself(int tokensToTransfer) {
        this.amountBuffer = Amount.ofSubunits(account1.getOwnNativeTokenBalance().getAmount());
        this.txBuffer = account1.transfer(account1, Amount.ofTokens(tokensToTransfer), Optional.empty());
    }

    @And("I transfer {int} XRD from the first account to the second")
    public void i_transfer_xrd_from_the_first_account_to_the_second(int amount) {
        var account1BalanceBefore = account1.getOwnNativeTokenBalance().getAmount();
        var account2BalanceBefore = account2.getOwnNativeTokenBalance().getAmount();
        var transferredAmount = Amount.ofTokens(amount);

        var transferTxId = account1.transfer(account2, transferredAmount, Optional.of("hello!"));
        var fee = account1.lookup(transferTxId).getFee();

        assertEquals(account1BalanceBefore.subtract(transferredAmount.toSubunits()).subtract(fee),
            account1.getOwnNativeTokenBalance().getAmount());
        assertEquals(account2BalanceBefore.add(transferredAmount.toSubunits()),
            account2.getOwnNativeTokenBalance().getAmount());
    }

    @Then("I have the same amount of tokens, minus fees")
    public void i_have_the_same_amount_of_tokens_minus_fees() {
        var amount = Amount.ofSubunits(account1.getOwnNativeTokenBalance().getAmount());
        var expectedAmount = amountBuffer.toSubunits().subtract(account1.lookup(txBuffer).getFee());
        assertEquals(expectedAmount, amount.toSubunits());
    }

    @Then("the second account can transfer {int} XRD back to the first")
    public void that_account_can_transfer_xrd_back_to_me(int amount) {
        var account1BalanceBefore = account1.getOwnNativeTokenBalance().getAmount();
        var account2BalanceBefore = account2.getOwnNativeTokenBalance().getAmount();
        var transferredAmount = Amount.ofTokens(amount);

        var transferTxId = account2.transfer(account1, transferredAmount, Optional.of("hello back!"));
        var fee = account2.lookup(transferTxId).getFee();

        assertEquals(account2BalanceBefore.subtract(transferredAmount.toSubunits()).subtract(fee),
            account2.getOwnNativeTokenBalance().getAmount());
        assertEquals(account1BalanceBefore.add(transferredAmount.toSubunits()),
            account1.getOwnNativeTokenBalance().getAmount());
    }

    @Then("I cannot transfer {int} XRD to another account")
    public void i_cannot_transfer_xrd_to_another_account(int tokensToTransfer) {
        boolean hasTransferred;
        try {
            account1.transfer(account2, Amount.ofTokens(tokensToTransfer), Optional.empty());
            hasTransferred = true;
        } catch (RadixApiException e) {
            hasTransferred = false;
        }
        assertFalse(hasTransferred);
    }

}
