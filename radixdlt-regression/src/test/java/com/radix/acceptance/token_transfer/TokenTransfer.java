package com.radix.acceptance.token_transfer;

import com.radix.acceptance.AcceptanceTest;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.utils.UInt256;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class TokenTransfer extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    @Given("I have two accounts with funds at a suitable Radix network")
    public void i_have_two_accounts_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
        faucet(account2);
    }

    @And("I transfer {int} XRD from the first account to the second")
    public void i_transfer_xrd_from_the_first_account_to_the_second(int amount) {
        var account1BalanceBefore = account1.getOwnNativeTokenBalance().getAmount();
        var account2BalanceBefore = account2.getOwnNativeTokenBalance().getAmount();
        var transferredAmount = Amount.ofTokens(amount);

        var transferTxId = account1.transfer(account2, transferredAmount, Optional.of("hello!"));
        UInt256 fee = account1.lookup(transferTxId).getFee();

        assertEquals(account1BalanceBefore.subtract(transferredAmount.toSubunits().subtract(fee)),
            account1.getOwnNativeTokenBalance().getAmount());
        assertEquals(account2BalanceBefore.add(transferredAmount.toSubunits()),
            account2.getOwnNativeTokenBalance().getAmount());
    }

    @Then("the second account can transfer {int} XRD back to the first")
    public void that_account_can_transfer_xrd_back_to_me(int amount) {
        var account1BalanceBefore = account1.getOwnNativeTokenBalance().getAmount();
        var account2BalanceBefore = account2.getOwnNativeTokenBalance().getAmount();
        var transferredAmount = Amount.ofTokens(amount);

        var transferTxId = account2.transfer(account1, transferredAmount, Optional.of("hello back!"));
        UInt256 fee = account2.lookup(transferTxId).getFee();

        assertEquals(account2BalanceBefore.subtract(transferredAmount.toSubunits().subtract(fee)),
            account2.getOwnNativeTokenBalance().getAmount());
        assertEquals(account1BalanceBefore.add(transferredAmount.toSubunits()),
            account1.getOwnNativeTokenBalance().getAmount());
    }

}
