package com.radix.acceptance.token_transfer;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.TransactionUtils;
import com.radix.test.Utils;
import com.radix.test.account.Account;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.client.lib.dto.BalanceDTO;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenTransfer extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    @Given("I have two accounts with funds at a suitable Radix network")
    public void i_have_two_accounts_with_funds_at_a_suitable_radix_network() {
        Account account1 = getTestAccount(0);
        Account account2 = getTestAccount(1);
        faucet(account1.getAddress());
        Utils.waitForBalance(account1, 10);
        faucet(account2.getAddress());
        Utils.waitForBalance(account2, 10);
    }

    @And("I transfer {int} XRD from the first account to the second")
    public void i_transfer_xrd_from_the_first_account_to_the_second(Integer xrdToTransfer) {
        Account account1 = getTestAccount(0);
        Account account2 = getTestAccount(1);

        TransactionUtils.performNativeTokenTransfer(account1, account2, xrdToTransfer, "hello there!");

        Utils.waitForBalance(account2, 15);
        Utils.waitForBalance(account1, 6);
    }

    @Then("the second account can transfer {int} XRD back to the first")
    public void that_account_can_transfer_xrd_back_to_me(Integer xrdToTransfer) {
        Account account1 = getTestAccount(0);
        Account account2 = getTestAccount(1);

        TransactionUtils.performNativeTokenTransfer(account2, account1, xrdToTransfer, "hey");
        Utils.waitForBalance(account2, 10);
        System.out.println(account1.getOwnNativeTokenBalance());
        System.out.println(account2.getOwnNativeTokenBalance());
    }

}
