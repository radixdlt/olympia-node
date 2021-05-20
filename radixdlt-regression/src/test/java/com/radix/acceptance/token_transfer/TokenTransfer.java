package com.radix.acceptance.token_transfer;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.TransactionUtils;
import com.radix.test.Utils;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.utils.functional.Result;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenTransfer extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    @Given("I have two accounts with funds at a suitable Radix network")
    public void i_have_two_accounts_with_funds_at_a_suitable_radix_network() {
        var account1 = getTestAccount(0);
        var account2 = getTestAccount(1);

        faucet(account1.getAddress());
        Utils.waitForBalanceToReach(account1, FAUCET_AMOUNT);
        faucet(account2.getAddress());
        Utils.waitForBalanceToReach(account2, FAUCET_AMOUNT);
    }

    @And("I transfer {int} XRD from the first account to the second")
    public void i_transfer_xrd_from_the_first_account_to_the_second(Integer xrdToTransfer) {
        var account1 = getTestAccount(0);
        var account2 = getTestAccount(1);

        Result<TxDTO> result = TransactionUtils.performNativeTokenTransfer(account1, account2, xrdToTransfer, "hello there!");

        var expectedBalance1 = FAUCET_AMOUNT.subtract(Utils.fromMajorToMinor(xrdToTransfer)).subtract(FIXED_FEES);
        Utils.waitForBalanceToReach(account1, expectedBalance1);
        var expectedBalance2 = FAUCET_AMOUNT.add(Utils.fromMajorToMinor(xrdToTransfer));
        Utils.waitForBalanceToReach(account2, expectedBalance2);
    }

    @Then("the second account can transfer {int} XRD back to the first")
    public void that_account_can_transfer_xrd_back_to_me(Integer xrdToTransfer) {
        var account1 = getTestAccount(0);
        var startingAmount1 = account1.getOwnNativeTokenBalance().getAmount();
        var account2 = getTestAccount(1);
        var startingAmount2 = account2.getOwnNativeTokenBalance().getAmount();

        TransactionUtils.performNativeTokenTransfer(account2, account1, xrdToTransfer, "hey");

        var expectedBalance1 = startingAmount1.add(Utils.fromMajorToMinor(xrdToTransfer));
        Utils.waitForBalanceToReach(account1, expectedBalance1);
        var expectedBalance2 = startingAmount2.subtract(Utils.fromMajorToMinor(xrdToTransfer)).subtract(FIXED_FEES);
        Utils.waitForBalanceToReach(account2, expectedBalance2);
    }

}
