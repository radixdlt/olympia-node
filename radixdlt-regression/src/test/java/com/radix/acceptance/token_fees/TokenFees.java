package com.radix.acceptance.token_fees;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.TransactionUtils;
import com.radix.test.Utils;
import com.radix.test.account.Account;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.IntStream;

public class TokenFees extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        Account account = getTestAccount();
        faucet(account.getAddress());
        Utils.waitForBalanceToReach(account, FAUCET_AMOUNT);
    }

    @When("I submit {int} transactions")
    public void i_submit_ten_transactions(Integer numberOfTransactions) {
        Account account = getTestAccount();
        IntStream.range(0, numberOfTransactions).forEach(count -> {
            TransactionUtils.performNativeTokenTransfer(account, account, 1);
            Utils.waitForBalanceToDecrease(account);
        });
    }

    @Then("I can observe that I have paid {int}XRD in fees")
    public void i_can_observe_that_i_have_paid_1xrd_in_fees(int parameter) {
        Utils.waitForBalanceToReach(getTestAccount(), FAUCET_AMOUNT.subtract(Utils.fromMajorToMinor(parameter)));
    }

}
