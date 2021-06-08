package com.radix.acceptance.transaction_lookup;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.Assertions;
import com.radix.test.TransactionUtils;
import com.radix.test.Utils;
import com.radixdlt.client.lib.dto.TransactionStatus;
import com.radixdlt.client.lib.dto.TxDTO;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;

import java.util.Objects;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TransactionLookup extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private static final int FIXED_TRANSFERAL_AMOUNT = 1;

    private TxDTO transaction;
    private int expectAmountMajor;

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        callFaucetAndWaitForTokens(account1);
    }

    @Given("I transfer {int} XRD anywhere")
    public void i_transfer_xrd_anywhere(Integer amountMajor) {
        expectAmountMajor = amountMajor;
        transaction = TransactionUtils.performNativeTokenTransferAndFold(account1, account2, expectAmountMajor);
        logger.info("Submitted transaction {}", transaction.getTxId());
    }

    @Then("I can lookup my transaction and observe it contains the expected information")
    public void i_can_lookup_my_transaction_and_observe_it_contains_the_expected_information() {
        var transactionDto = await().atMost(Durations.FIVE_SECONDS).ignoreExceptions()
            .until(() -> account1.transaction().lookup(transaction.getTxId()).fold(Utils::toTestFailureException,
			transactionDTO -> transactionDTO), Objects::nonNull);

        Assertions.assertNativeTokenTransferTransaction(account1, account2, expectAmountMajor, transactionDto);
    }

    @Given("I perform {int} token transfers")
    public void i_perform_token_transfers(Integer numOfTransactions) {
        IntStream.range(0, numOfTransactions).forEach(count -> {
            var receiver = getTestAccount(count);
            TransactionUtils.performNativeTokenTransfer(account1, receiver, FIXED_TRANSFERAL_AMOUNT);
            Utils.waitForBalanceToChange(receiver);
        });
    }

    @Then("I can observe those {int} transactions in my transaction history")
    public void i_can_observe_those_transactions_in_my_transaction_history(Integer numOfTransactions) {
        var historyDTO = account1.account().history(account1.getAddress(), numOfTransactions, Optional.empty())
            .fold(Utils::toTestFailureException, historyDto -> historyDto);

        var transactions = historyDTO.getTransactions();
        assertEquals(numOfTransactions.intValue(), transactions.size());
        // This is a way to test ordering. The most recent transaction should be first in this list.
        Collections.reverse(transactions);
        IntStream.range(0, numOfTransactions).forEach(count -> {
            var transactionDTO = transactions.get(count);
            var receiver = getTestAccount(count);
            Assertions.assertNativeTokenTransferTransaction(account1, receiver, FIXED_TRANSFERAL_AMOUNT, transactionDTO);
        });
    }

    @Then("I can check the status of my transaction")
    public void i_can_check_the_status_of_my_transaction() {
        var transactionStatus = TransactionUtils.performTxStatusRequest(account1, transaction.getTxId())
            .getStatus();
        assertTrue(transactionStatus.equals(TransactionStatus.PENDING)
            || transactionStatus.equals(TransactionStatus.CONFIRMED));
    }

    @Then("It should be quickly CONFIRMED")
    public void it_should_be_quickly_confirmed() {
        await().atMost(Durations.FIVE_SECONDS).until(() ->
            TransactionUtils.performTxStatusRequest(account1, transaction.getTxId()).getStatus()
                .equals(TransactionStatus.CONFIRMED)
        );
    }

}
